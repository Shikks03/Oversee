import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { FirebaseError } from "firebase-admin/app";

admin.initializeApp();

const ALERT_SECRET = "oversee-alert-v1";
const RATE_LIMIT_MS = 60_000;

export const sendHighSeverityAlert = functions.https.onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("Method Not Allowed");
    return;
  }

  // #4 — Reject requests without valid shared secret
  if (req.headers["x-oversee-secret"] !== ALERT_SECRET) {
    res.status(401).send("Unauthorized");
    return;
  }

  const deviceId: string = req.body?.device_id;
  if (!deviceId) {
    res.status(400).send("Missing device_id");
    return;
  }

  let parentDocRef: admin.firestore.DocumentReference | null = null;

  try {
    const parentQuery = await admin.firestore()
      .collection("users")
      .where("linked_child_id", "==", deviceId)
      .where("role", "==", "parent")
      .limit(1)
      .get();

    if (parentQuery.empty) {
      console.log(`No parent linked to device ${deviceId}`);
      res.status(200).send("No parent linked");
      return;
    }

    const parentDoc = parentQuery.docs[0];
    parentDocRef = parentDoc.ref;
    const parentData = parentDoc.data();

    // #5 — Per-device rate limit: minimum 60 seconds between alerts
    const lastAlertTs: number = parentData.last_alert_ts ?? 0;
    const now = Date.now();
    if (now - lastAlertTs < RATE_LIMIT_MS) {
      console.log(`Rate limit hit for device ${deviceId}, skipping`);
      res.status(200).send("Rate limited");
      return;
    }

    // #12 — Reject non-string or empty fcm_token before sending
    const fcmToken = parentData.fcm_token;
    if (typeof fcmToken !== "string" || fcmToken.length === 0) {
      console.log(`Parent has no valid FCM token for device ${deviceId}`);
      res.status(200).send("Parent has no FCM token");
      return;
    }

    // #17 — Omit child_device_id from push payload
    const message: admin.messaging.Message = {
      token: fcmToken,
      data: {
        type: "HIGH_SEVERITY_INCIDENT",
        timestamp: String(now),
      },
      android: {
        priority: "high",
      },
    };

    await admin.messaging().send(message);
    await parentDocRef.update({ last_alert_ts: now });

    console.log(`Alert sent to parent for device ${deviceId}`);
    res.status(200).send("Alert sent");
  } catch (error: unknown) {
    // #3 — Correct FirebaseError instanceof check for stale tokens
    if (
      error instanceof FirebaseError &&
      error.code === "messaging/registration-token-not-registered"
    ) {
      // #7 — Purge stale token so future alerts don't hit the same dead token
      if (parentDocRef) {
        await parentDocRef.update({
          fcm_token: admin.firestore.FieldValue.delete(),
        });
      }
      console.warn(`Stale FCM token for device ${deviceId}, purged from Firestore`);
      res.status(200).send("Stale token, skipped");
    } else {
      console.error("Failed to send alert:", error);
      return res.status(500).send("Internal error"); // #16 — return prevents latent double-response
    }
  }
});
