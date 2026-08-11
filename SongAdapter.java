package com.example.musiclite;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class SongAdapter extends BaseAdapter {

    private Context context;
    private List<MainActivity.Song> list;

    public SongAdapter(Context context, List<MainActivity.Song> list) {
        this.context = context;
        this.list = list;
    }

    @Override
    public int getCount() { return list.size(); }

    @Override
    public Object getItem(int position) { return list.get(position); }

    @Override
    public long getItemId(int position) { return position; }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false);
        }

        MainActivity.Song item = list.get(position);

        TextView tvTitle = convertView.findViewById(R.id.tvTitle);
        TextView tvArtist = convertView.findViewById(R.id.tvArtist);
        ImageView ivAlbumArt = convertView.findViewById(R.id.ivAlbumArt);

        tvTitle.setText(item.title);
        tvArtist.setText(item.artist);

        Bitmap albumArt = getAlbumArt(item.path);
        if (albumArt != null) {
            ivAlbumArt.setImageBitmap(albumArt);
        } else {
            ivAlbumArt.setImageResource(android.R.drawable.ic_media_play);
        }

        return convertView;
    }

    private Bitmap getAlbumArt(String path) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(path);
            byte[] artBytes = retriever.getEmbeddedPicture();
            if (artBytes != null) {
                return BitmapFactory.decodeByteArray(artBytes, 0, artBytes.length);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}