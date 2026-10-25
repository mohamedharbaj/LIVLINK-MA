// كيمسح: 1) كل إعلان فات عليه وقت الانتهاء (expiresAt)
//        2) كل إعلان توصل بـ REPORT_THRESHOLD بلاغات أو أكثر (مع بلاغاتو)
//        3) البلاغات القديمة (أكثر من 30 يوم)
const admin = require("firebase-admin");

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();

// 5 بلاغات من 5 حسابات مختلفة (رفعناها من 3 لتصعيب التلاعب بحسابات وهمية)
const REPORT_THRESHOLD = 5;
const REPORT_TTL_MS = 30 * 24 * 60 * 60 * 1000;

async function deleteExpiredAds() {
  const now = admin.firestore.Timestamp.now();
  let total = 0;

  while (true) {
    const snap = await db.collection("ads").where("expiresAt", "<", now).limit(400).get();
    if (snap.empty) break;

    const batch = db.batch();
    snap.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
    total += snap.size;
  }

  console.log(`Deleted ${total} expired ads.`);
}

async function deleteReportedAds() {
  const snap = await db.collection("reports").limit(2000).get();
  const byAd = new Map();
  snap.docs.forEach((doc) => {
    const adId = doc.get("adId");
    if (!adId) return;
    if (!byAd.has(adId)) byAd.set(adId, []);
    byAd.get(adId).push(doc.ref);
  });

  let removed = 0;
  for (const [adId, refs] of byAd) {
    if (refs.length < REPORT_THRESHOLD) continue;
    const batch = db.batch();
    batch.delete(db.collection("ads").doc(adId));
    refs.slice(0, 400).forEach((ref) => batch.delete(ref));
    await batch.commit();
    removed++;
  }

  console.log(`Removed ${removed} reported ads (threshold ${REPORT_THRESHOLD}).`);
}

async function deleteOldReports() {
  const cutoff = Date.now() - REPORT_TTL_MS;
  let total = 0;

  while (true) {
    const snap = await db.collection("reports").where("createdAt", "<", cutoff).limit(400).get();
    if (snap.empty) break;

    const batch = db.batch();
    snap.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
    total += snap.size;
  }

  console.log(`Deleted ${total} old reports.`);
}

async function main() {
  await deleteExpiredAds();
  await deleteReportedAds();
  await deleteOldReports();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
