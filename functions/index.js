const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");

admin.initializeApp();

exports.sendAdminNotification = onDocumentCreated(
  "admin_notification_requests/{requestId}",
  async (event) => {
    const snap = event.data;

    if (!snap) {
      logger.error("No document data");
      return;
    }

    const requestId = event.params.requestId;
    const data = snap.data();

    const title = data.title || "مصاريفي برو";
    const body = data.body || "";
    const messageType = data.messageType || "push";
    const topic = data.targetTopic || "all_users";

    if (!title || !body) {
      await snap.ref.update({
        status: "failed",
        errorMessage: "Missing title or body",
        failedAt: admin.firestore.FieldValue.serverTimestamp()
      });
      return;
    }

    if (messageType === "in_app") {
      await snap.ref.update({
        status: "sent",
        sentAt: admin.firestore.FieldValue.serverTimestamp(),
        note: "In-app message only. No push notification sent."
      });
      return;
    }

    const message = {
      topic: topic,
      notification: {
        title: title,
        body: body
      },
      data: {
        title: title,
        body: body,
        requestId: requestId,
        messageType: messageType
      },
      android: {
        priority: "high",
        notification: {
          channelId: "general_notifications",
          sound: "default"
        }
      }
    };

    try {
      const messageId = await admin.messaging().send(message);

      await snap.ref.update({
        status: "sent",
        sentAt: admin.firestore.FieldValue.serverTimestamp(),
        fcmMessageId: messageId,
        errorMessage: ""
      });

      logger.info("Notification sent", { requestId, messageId });
    } catch (error) {
      await snap.ref.update({
        status: "failed",
        failedAt: admin.firestore.FieldValue.serverTimestamp(),
        errorMessage: error.message || String(error)
      });

      logger.error("Notification send failed", error);
    }
  }
);