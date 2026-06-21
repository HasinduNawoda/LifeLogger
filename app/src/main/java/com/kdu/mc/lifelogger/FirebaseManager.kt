package com.kdu.mc.lifelogger

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

object FirebaseManager {

    val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val firestore: FirebaseFirestore =
        FirebaseFirestore.getInstance()

    val storage: FirebaseStorage =
        FirebaseStorage.getInstance()
}