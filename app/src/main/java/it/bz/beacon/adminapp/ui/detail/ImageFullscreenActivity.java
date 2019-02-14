package it.bz.beacon.adminapp.ui.detail;

import android.content.Context;
import android.content.ContextWrapper;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;
import it.bz.beacon.adminapp.R;
import it.bz.beacon.adminapp.ui.BaseActivity;

public class ImageFullscreenActivity extends BaseActivity {

    public static final String EXTRA_IMAGE_FILENAME = "EXTRA_IMAGE_FILENAME";

    @BindView(R.id.image)
    protected ImageView img;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ButterKnife.bind(this);
        setSupportActionBar(toolbar);
        setUpToolbar();

        if (getIntent() != null) {
            String fileName = getIntent().getStringExtra(EXTRA_IMAGE_FILENAME);

            ContextWrapper contextWrapper = new ContextWrapper(this);
            File directory = contextWrapper.getDir(getString(R.string.image_folder), Context.MODE_PRIVATE);
            final File file = new File(directory, fileName);

            if (file.exists()) {
                Picasso.with(this)
                        .load(file)
                        .placeholder(R.drawable.placeholder)
                        .into(img);
            }
        }
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_image_fullscreen;
    }

    private void setUpToolbar() {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_arrow_back);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case android.R.id.home:
                finish();
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
