package com.pnj.marketplace_perikanan

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.*
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.pnj.marketplace_perikanan.auth.SettingsActivity
import com.pnj.marketplace_perikanan.chat.ChatActivity
import com.pnj.marketplace_perikanan.databinding.ActivityMainBinding
import com.pnj.marketplace_perikanan.ikan.AddIkanActivity
import com.pnj.marketplace_perikanan.ikan.Ikan
import com.pnj.marketplace_perikanan.ikan.IkanAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var ikanRecyclerView: RecyclerView
    private lateinit var ikanArrayList: ArrayList<Ikan>
    private lateinit var ikanAdapter: IkanAdapter
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ikanRecyclerView = binding.makananListView
        ikanRecyclerView.layoutManager = LinearLayoutManager(this)
        ikanRecyclerView.setHasFixedSize(true)

        ikanArrayList = arrayListOf()
        ikanAdapter = IkanAdapter(ikanArrayList)

        ikanRecyclerView.adapter = ikanAdapter

        load_data()
        swipeDelete()

        binding.btnAddIkan.setOnClickListener {
            val intentMain = Intent(this, AddIkanActivity::class.java)
            startActivity(intentMain)
        }

        // TextChangedListener
        binding.txtSearchIkan.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                val keyword = binding.txtSearchIkan.text.toString()

                if (keyword.isNotEmpty()) {
                    search_data(keyword)
                }
                else {
                    load_data()
                }

            }

            override fun afterTextChanged(p0: Editable?) {}
        })

        binding.bottomNavigation.setOnItemSelectedListener {
            when(it.itemId) {
                R.id.nav_bottom_home -> {
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                }
                R.id.nav_bottom_setting -> {
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                }
                R.id.nav_bottom_chat -> {
                    val intent = Intent(this, ChatActivity::class.java)
                    startActivity(intent)
                }
            }
            true
        }



    }

    private fun load_data() {
        ikanArrayList.clear()
        db = FirebaseFirestore.getInstance()
        db.collection("ikan").
                addSnapshotListener(object : EventListener<QuerySnapshot> {
                    override fun onEvent(
                        value: QuerySnapshot?,
                        error: FirebaseFirestoreException?
                    ) {
                        if (error != null) {
                            Log.e("Firestore Error", error.message.toString())
                            return
                        }
                        for (dc: DocumentChange in value?.documentChanges!!){
                            if (dc.type == DocumentChange.Type.ADDED)
                                ikanArrayList.add(dc.document.toObject(Ikan::class.java))
                        }
                        ikanAdapter.notifyDataSetChanged()
                    }
                })
    }

    private fun search_data(keyword : String) {
        ikanArrayList.clear()

        db = FirebaseFirestore.getInstance()

        val query = db.collection("ikan")
            .orderBy("nama")
            .startAt(keyword)
            .get()
        query.addOnSuccessListener {
            ikanArrayList.clear()
            for (document in it) {
                ikanArrayList.add(document.toObject(Ikan::class.java))
            }
        }

    }

    private fun deleteIkan(ikan: Ikan, doc_id: String) {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("Apakah ${ikan.nama} ingin dihapus ?")
            .setCancelable(false)
            .setPositiveButton("Yes") { dialog, id ->
                lifecycleScope.launch {
                    db.collection("ikan")
                        .document(doc_id).delete()


                    deleteFoto("img_ikan/${ikan.nik}_${ikan.nama}.jpg")

                    Toast.makeText(
                        applicationContext,
                        ikan.nama.toString() + "is deleted",
                        Toast.LENGTH_LONG
                    ).show()
                    load_data()

                }

            }
            .setNegativeButton("No") { dialog, id ->
                dialog.dismiss()
                load_data()
            }

        val alert = builder.create()
        alert.show()
    }

    private fun swipeDelete() {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0,
            ItemTouchHelper.RIGHT)  {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                lifecycleScope.launch {
                    val ikan = ikanArrayList[position]
                    val personQuery = db.collection("ikan")
                        .whereEqualTo("nik", ikan.nik)
                        .whereEqualTo("nama", ikan.nama)
                        .whereEqualTo("jenis_kelamin", ikan.jenis_kelamin)
                        .whereEqualTo("tgl_lahir", ikan.tgl_lahir)
                        .whereEqualTo("penyakit_bawaan", ikan.penyakit_bawaan)
                        .get()
                        .await()

                    if (personQuery.documents.isNotEmpty()) {
                        for (document in personQuery) {
                            try {
                                deleteIkan(ikan, document.id)
                                load_data()
                            }
                            catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(
                                        applicationContext,
                                        e.message.toString(),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    }
                    else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                applicationContext,
                                "Ikan yang ingin di hapus tidak ditemukan",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }).attachToRecyclerView(ikanRecyclerView)

    }

    private fun deleteFoto(file_name: String){
        val storage = Firebase.storage
        val storageRef = storage.reference
        val deleteFileRef = storageRef.child(file_name)

        if (deleteFileRef != null) {
            deleteFileRef.delete().addOnSuccessListener {
                Log.e("deleted", "success")
            }.addOnFailureListener{
                Log.e("deleted", "failed")
            }
        }

    }







}