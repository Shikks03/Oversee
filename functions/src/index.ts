import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();

/**
 * HTTPS-triggered Cloud Function.
 * Called by the child device when a HIGH severity word is detected.
 *
 * Body: { "device_id": "123456789" }
 *
 * Flow:
 * 1. Find the parent user where linked_child_id == device_id
 * 2. Get the parent's FCM token
 * 3. Send a data message to the parent device
 */
export const sendHighSeverityAlert = functions.https.onRequest(async (req, res) => {
  // Only allow POST
  if (req.method !== "POST") {
    res.status(405).send("Method Not Allowed");
    return;
  }

  const deviceId: string = req.body?.device_id;
  if (!deviceId) {
    res.status(400).send("Missing device_id");
    return;
  }

  try {
    // 1. Find parent user linked to this child device
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

    const parentData = parentQuery.docs[0].data();
    const fcmToken: string = parentData.fcm_token;

    if (!fcmToken) {
      console.log(`Parent has no FCM token for device ${deviceId}`);
      res.status(200).send("Parent has no FCM token");
      return;
    }

    // 2. Send FCM data message to parent
    const message: admin.messaging.Message = {
      token: fcmToken,
      data: {
        type: "HIGH_SEVERITY_INCIDENT",
        child_device_id: deviceId,
        timestamp: String(Date.now()),
      },
      android: {
        priority: "high",
      },
    };

    await admin.messaging().send(message);
    console.log(`Alert sent to parent for device ${deviceId}`);
    res.status(200).send("Alert sent");
  } catch (error: unknown) {
    // Handle stale/invalid FCM token gracefully
    if (
      error instanceof Error &&
      "code" in (error as NodeJS.ErrnoException) &&
      (error as NodeJS.ErrnoException).code === "messaging/registration-token-not-registered"
    ) {
      console.warn(`Stale FCM token for device ${deviceId}, skipping`);
      res.status(200).send("Stale token, skipped");
    } else {
      console.error("Failed to send alert:", error);
      res.status(500).send("Internal error");
    }
  }
});
