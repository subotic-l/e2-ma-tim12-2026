const admin = require("firebase-admin");

// Service account is passed via FIREBASE_SERVICE_ACCOUNT secret (JSON string)
const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const db = admin.firestore();
const CHAT_COLLECTION = "region_chats";
const USERS_COLLECTION = "users";
const NOTIFICATIONS_COLLECTION = "notifications";

/**
 * Finds users whose lastSeen is older than 5 minutes
 * and sends them FCM push for new unread notifications.
 */
async function main() {
  const fiveMinutesAgo = Date.now() - 5 * 60 * 1000;

  // Get all users who have an FCM token
  const usersSnap = await db
    .collection(USERS_COLLECTION)
    .where("fcmToken", "!=", null)
    .get();

  console.log(`Found ${usersSnap.size} users with FCM tokens`);

  for (const doc of usersSnap.docs) {
    const uid = doc.id;
    const data = doc.data();
    const fcmToken = data.fcmToken;

    if (!fcmToken) continue;

    // Check if user is offline (lastSeen > 5 min ago or null)
    const lastSeen = data.lastSeen ? data.lastSeen.toMillis() : 0;
    if (lastSeen && lastSeen >= fiveMinutesAgo) continue; // user is active

    // Find unread notifications for this user (newest first)
    const notifsSnap = await db
      .collection(USERS_COLLECTION)
      .doc(uid)
      .collection(NOTIFICATIONS_COLLECTION)
      .where("read", "==", false)
      .orderBy("createdAt", "desc")
      .limit(1)
      .get();

    if (notifsSnap.empty) continue;

    const latest = notifsSnap.docs[0].data();
    const body = latest.message || "Nova poruka";
    const channelId = latest.channel || "chat";

    try {
      const response = await admin.messaging().send({
        token: fcmToken,
        notification: {
          title: "Slagalica",
          body,
        },
        data: {
          channelId,
          title: "Slagalica",
          body,
        },
      });

      console.log(`Sent to ${uid}: ${response}`);
    } catch (err) {
      // Token might be invalid — remove it
      if (err.code === "messaging/invalid-registration-token" || err.code === "messaging/registration-token-not-registered") {
        console.log(`Removing invalid token for ${uid}`);
        await db.collection(USERS_COLLECTION).doc(uid).update({
          fcmToken: admin.firestore.FieldValue.delete(),
        });
      } else {
        console.error(`Error sending to ${uid}:`, err.message);
      }
    }
  }

  console.log("Done");
}

main().catch(console.error);
