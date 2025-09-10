package com.jacob.fruitoftek.localfacerecognition;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import com.jacob.fruitoftek.localfacerecognition.DB.DBHelper;
import com.jacob.fruitoftek.localfacerecognition.Face_Recognition.FaceClassifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DaftarNamaActivity extends AppCompatActivity {
    private ListView listView;
    private DBHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daftar_nama);

        // Inisialisasi ListView
        listView = findViewById(R.id.listView);

        // Inisialisasi DBHelper
        dbHelper = new DBHelper(this);

        // Inisialisasi EditText untuk pencarian
        EditText editTextSearch = findViewById(R.id.editTextSearch);

        // Ambil semua data wajah dari database
        HashMap<String, FaceClassifier.Recognition> semuaWajah = dbHelper.getAllFaces();

        // Ekstrak nama-nama dari data wajah
        List<String> daftarNama = new ArrayList<>(semuaWajah.keySet());

        // Tampilkan data nama dalam ListView
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, daftarNama);
        listView.setAdapter(adapter);

        // Menambahkan listener untuk pencarian
        editTextSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Filter adapter berdasarkan teks pencarian
                adapter.getFilter().filter(s.toString());
            }
        });
    }
}
