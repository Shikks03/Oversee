import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();

const ALERT_SECRET = "oversee-alert-v1";
const RATE_LIMIT_MS = 60_000;

export const sendHighSeverityAlert = functions.https.onRequest(async (req, res): Promise<void> => {
  if (req.method !== "POST") {
    res.status(405).send("Method Not Allowed");
    return;
  }

  if (req.headers["x-oversee-secret"] !== ALERT_SECRET) {
    res.status(401).send("Unauthorized");
    return;
  }

  const uid: string = req.body?.uid;
  const childFid: string = req.body?.child_fid;
  if (!uid || !childFid) {
    res.status(400).send("Missing uid or child_fid");
    return;
  }

  let parentDeviceRef: admin.firestore.DocumentReference | null = null;

  try {
    const userDoc = await admin.firestore()
      .collection("users")
      .doc(uid)
      .get();

    if (!userDoc.exists) {
      console.log(`No user document found for uid ${uid}`);
      res.status(200).send("No parent linked");
      return;
    }

    const userData = userDoc.data()!;
    const parentDeviceFid: string | undefined = userData.parent_device_fid;

    if (!parentDeviceFid) {
      console.log(`No parent_device_fid on user ${uid}`);
      res.status(200).send("No parent linked");
      return;
    }

    const parentDeviceDoc = await admin.firestore()
      .collection("users")
      .doc(uid)
      .collection("devices")
      .doc(parentDeviceFid)
      .get();

    if (!parentDeviceDoc.exists) {
      console.log(`Parent device doc missing for uid ${uid}, fid ${parentDeviceFid}`);
      res.status(200).send("No parent linked");
      return;
    }

    parentDeviceRef = parentDeviceDoc.ref;
    const parentDeviceData = parentDeviceDoc.data()!;

    const childDeviceRef = admin.firestore()
      .collection("users")
      .doc(uid)
      .collection("devices")
      .doc(childFid);
    const childDeviceDoc = await childDeviceRef.get();
    const childData = childDeviceDoc.exists ? childDeviceDoc.data()! : {};
    const childName: string = typeof childData.child_name === "string" && childData.child_name.length > 0
      ? childData.child_name
      : "your child";

    // Per-child rate limit (was global on the parent device doc)
    const lastAlertTs: number = childData.last_alert_ts ?? 0;
    const now = Date.now();
    if (now - lastAlertTs < RATE_LIMIT_MS) {
      console.log(`Rate limit hit for uid ${uid}, child ${childFid}, skipping`);
      res.status(200).send("Rate limited");
      return;
    }

    const fcmToken = parentDeviceData.fcm_token;
    if (typeof fcmToken !== "string" || fcmToken.length === 0) {
      console.log(`Parent has no valid FCM token for uid ${uid}`);
      res.status(200).send("No parent linked");
      return;
    }

    const message: admin.messaging.Message = {
      token: fcmToken,
      data: {
        type: "HIGH_SEVERITY_INCIDENT",
        timestamp: String(now),
        child_fid: childFid,
        child_name: childName,
      },
      android: {
        priority: "high",
      },
    };

    await admin.messaging().send(message);
    await childDeviceRef.set({ last_alert_ts: now }, { merge: true });

    console.log(`Alert sent to parent for uid ${uid}, child_fid ${childFid}`);
    res.status(200).send("Alert sent");
  } catch (error: unknown) {
    if (
      (error as any)?.code === "messaging/registration-token-not-registered"
    ) {
      if (parentDeviceRef) {
        await parentDeviceRef.update({
          fcm_token: admin.firestore.FieldValue.delete(),
        });
      }
      console.warn(`Stale FCM token for uid ${uid}, purged from Firestore`);
      res.status(200).send("Stale token, skipped");
    } else {
      console.error("Failed to send alert:", error);
      res.status(500).send("Internal error");
    }
  }
});
