package com.example

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Date

/**
 * طبقة الاتصال بـ Firebase:
 *  - تسجيل دخول مجهول (بلا إيميل ولا SMS) باش كل إعلان يتربط بصاحبو (ownerId).
 *  - مجموعة "ads" فـ Firestore فيها كاع الإعلانات (طلبات المحلات + عروض الليفرورات).
 */
object CloudRepo {
    private const val ADS = "ads"
    private const val MAX_RESULTS = 100L

    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    val uid: String? get() = auth.currentUser?.uid

    fun ensureSignedIn(onReady: (String?) -> Unit) {
        val current = auth.currentUser
        if (current != null) {
            onReady(current.uid)
            return
        }
        auth.signInAnonymously().addOnCompleteListener { task ->
            onReady(if (task.isSuccessful) task.result?.user?.uid else null)
        }
    }

    /** كيتسمع للإعلانات. إلا اختارت مدينة كنفلتر بها من السيرفر باش نقللو القراءات. */
    fun listenAds(
        city: String,
        onChange: (List<DeliveryOffer>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val base = db.collection(ADS)
        val query: Query =
            if (city == "جميع مدن المغرب") base.orderBy("createdAt", Query.Direction.DESCENDING).limit(MAX_RESULTS)
            else base.whereEqualTo("city", city).orderBy("createdAt", Query.Direction.DESCENDING).limit(MAX_RESULTS)

        return query.addSnapshotListener { snap, err ->
            if (err != null) {
                onError(err)
                return@addSnapshotListener
            }
            val list = snap?.documents
                ?.mapNotNull { it.toOffer() }
                ?.filter { isFreshOffer(it.createdAt) }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
            onChange(list)
        }
    }

    fun publish(offer: DeliveryOffer, onDone: (Boolean) -> Unit) {
        val data = hashMapOf<String, Any?>(
            "title" to offer.title,
            "city" to offer.city,
            "district" to offer.district,
            "roleType" to offer.roleType,
            "phone" to offer.phone,
            "details" to offer.details,
            "vehicleType" to offer.vehicleType,
            "priceEstimate" to offer.priceEstimate,
            "ownerId" to offer.ownerId,
            "createdAt" to offer.createdAt,
            // حقل TTL: Firestore كيمسح الإعلان وحدو منين توصل هاد الوقت (بعد ما تفعل TTL فالكونسول)
            "expiresAt" to Timestamp(Date(offer.createdAt + OFFER_TTL_MS))
        )
        offer.latitude?.let { data["latitude"] = it }
        offer.longitude?.let { data["longitude"] = it }
        offer.locationAddress?.let { data["locationAddress"] = it }

        db.collection(ADS).document(offer.id).set(data)
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun delete(id: String, onDone: (Boolean) -> Unit) {
        db.collection(ADS).document(id).delete()
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    /** بلاغ عن إعلان. معرّف البلاغ = adId_uid باش المستخدم ما يبلغش مرتين على نفس الإعلان. */
    fun report(adId: String, reason: String, onDone: (Boolean) -> Unit) {
        val me = uid
        if (me == null) {
            onDone(false)
            return
        }
        val data = hashMapOf<String, Any>(
            "adId" to adId,
            "reporterId" to me,
            "reason" to reason,
            "createdAt" to System.currentTimeMillis()
        )
        db.collection("reports").document("${adId}_$me").set(data)
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    fun newId(): String = db.collection(ADS).document().id

    private fun DocumentSnapshot.toOffer(): DeliveryOffer? {
        val title = getString("title") ?: return null
        val created = getLong("createdAt") ?: 0L
        return DeliveryOffer(
            id = id,
            title = title,
            city = getString("city") ?: "",
            district = getString("district") ?: "اختر الحي",
            roleType = getString("roleType") ?: "طلب توصيل",
            phone = getString("phone") ?: "",
            timeAgo = "مؤخراً",
            details = getString("details") ?: "",
            vehicleType = getString("vehicleType") ?: "دراجة نارية",
            priceEstimate = getString("priceEstimate") ?: "حسب الاتفاق",
            latitude = getDouble("latitude"),
            longitude = getDouble("longitude"),
            locationAddress = getString("locationAddress"),
            createdAt = created,
            ownerId = getString("ownerId") ?: ""
        )
    }
}

fun isFreshOffer(createdAt: Long): Boolean =
    createdAt <= 0L || System.currentTimeMillis() - createdAt <= OFFER_TTL_MS
