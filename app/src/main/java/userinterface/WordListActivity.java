package userinterface;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;

import com.example.android_codenames_repo.R;

import java.util.concurrent.atomic.AtomicInteger;

public class WordListActivity extends Activity
{
    GridView grid_view;
    Button yes_button;
    Button no_button;
    ArrayAdapter<String> adapter;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Bundle bundle = getIntent().getExtras();
        String[] string_array = bundle.getStringArray("word_list");

        Log.d("LIST SIZE", "" + string_array.length);

        setContentView(R.layout.wordlistactivity);

        adapter = new ArrayAdapter<>(this, R.layout.griditem, string_array);

        grid_view = findViewById(R.id.grid_view);

        grid_view.setAdapter(adapter);

        yes_button = findViewById(R.id.yes_button);
        no_button = findViewById(R.id.no_button);

        // Set click listener for the Yes button
        yes_button.setOnClickListener(view -> {
            // If Yes button is pressed
            CameraActivity.number_expected--;
            finish();
        });

        // Set click listener for the No button
        no_button.setOnClickListener(view -> {
            // If No button is pressed
            finish();
        });
    }
}
