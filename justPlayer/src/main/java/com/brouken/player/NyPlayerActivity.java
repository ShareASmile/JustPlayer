package com.brouken.player;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.util.Base64;
import android.view.View;
import android.view.accessibility.CaptioningManager;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory;
import androidx.media3.extractor.ts.TsExtractor;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.DefaultTimeBar;

import com.brouken.player.dtpv.DoubleTapPlayerView;
import com.brouken.player.encrypt.EncryptUtil;
import com.brouken.player.encrypt.EncryptedDataSourceFactory;
import com.brouken.player.encrypt.ExtendedExtractorsFactory;
import com.brouken.player.encrypt.NyFileUtil;
import com.brouken.player.encrypt.OnlineEncryptedDataSourceFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.OkHttpClient;

public class NyPlayerActivity extends PlayerActivity {

    //TODO ny
    public static void start(Context context, String path) {
        start(context, Uri.parse(path));
    }

    public static void start(Context context, Uri uri) {
        Intent intent = new Intent(context, NyPlayerActivity.class);
        intent.setData(uri);
        context.startActivity(intent);
    }

    //TODO ny
    @Override
    protected void setupPlayerWithSource(boolean isNetworkUri) {
        trackSelector = new DefaultTrackSelector(this);
        if (mPrefs.tunneling) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setTunnelingEnabled(true)
            );
        }
        switch (mPrefs.languageAudio) {
            case Prefs.TRACK_DEFAULT:
                break;
            case Prefs.TRACK_DEVICE:
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setPreferredAudioLanguages(Utils.getDeviceLanguages())
                );
                break;
            default:
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setPreferredAudioLanguages(mPrefs.languageAudio)
                );
        }
        final CaptioningManager captioningManager = (CaptioningManager) getSystemService(Context.CAPTIONING_SERVICE);
        if (!captioningManager.isEnabled()) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            );
        }
        Locale locale = captioningManager.getLocale();
        if (locale != null) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setPreferredTextLanguage(locale.getISO3Language())
            );
        }
        // https://github.com/google/ExoPlayer/issues/8571
        ExtendedExtractorsFactory extractorsFactory = new ExtendedExtractorsFactory();

        @SuppressLint("WrongConstant") RenderersFactory renderersFactory = new DefaultRenderersFactory(this)
                .setExtensionRendererMode(mPrefs.decoderPriority)
                .setMapDV7ToHevc(mPrefs.mapDV7ToHevc);

        ExoPlayer.Builder playerBuilder = new ExoPlayer.Builder(this, renderersFactory)
                .setTrackSelector(trackSelector);

        String path = NyFileUtil.getPath(this, mPrefs.mediaUri);

        DataSource.Factory encryptedFactory = null;
        if (path.contains("_NY")) encryptedFactory = getEncryptedMediaSource(this, path);

        if (encryptedFactory != null)
            playerBuilder.setMediaSourceFactory(new DefaultMediaSourceFactory(encryptedFactory,extractorsFactory));
        else if (haveMedia && isNetworkUri) {
            if (mPrefs.mediaUri.getScheme().toLowerCase().startsWith("http")) {
                HashMap<String, String> headers = new HashMap<>();
                String userInfo = mPrefs.mediaUri.getUserInfo();
                if (userInfo != null && userInfo.length() > 0 && userInfo.contains(":")) {
                    headers.put("Authorization", "Basic " + Base64.encodeToString(userInfo.getBytes(), Base64.NO_WRAP));
                    DefaultHttpDataSource.Factory defaultHttpDataSourceFactory = new DefaultHttpDataSource.Factory();
                    defaultHttpDataSourceFactory.setDefaultRequestProperties(headers);
                    playerBuilder.setMediaSourceFactory(new DefaultMediaSourceFactory(defaultHttpDataSourceFactory, extractorsFactory));
                }
            }
        } else
            playerBuilder.setMediaSourceFactory(new DefaultMediaSourceFactory(this, extractorsFactory));
        player = playerBuilder.build();
        //---------------------------//
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build();
        player.setAudioAttributes(audioAttributes, true);

        if (mPrefs.skipSilence) {
            player.setSkipSilenceEnabled(true);
        }

        if (mediaSession != null) {
            mediaSession.release();
        }

        if (player.canAdvertiseSession()) {
            try {
                mediaSession = new MediaSession.Builder(this, player).build();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            }
        }
        if (haveMedia) {
            MediaItem.Builder mediaItemBuilder = new MediaItem.Builder()
                    .setUri(mPrefs.mediaUri)
                    .setMimeType(mPrefs.mediaType);
            String title;
            if (apiTitle != null) {
                title = apiTitle;
            } else {
                title = Utils.getFileName(NyPlayerActivity.this, mPrefs.mediaUri);
            }
            if (title != null) {
                final MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                        .setTitle(title)
                        .setDisplayTitle(title)
                        .build();
                mediaItemBuilder.setMediaMetadata(mediaMetadata);
            }
            if (apiAccess && apiSubs.size() > 0) {
                mediaItemBuilder.setSubtitleConfigurations(apiSubs);
            } else if (mPrefs.subtitleUri != null && Utils.fileExists(this, mPrefs.subtitleUri)) {
                MediaItem.SubtitleConfiguration subtitle = SubtitleUtils.buildSubtitle(this, mPrefs.subtitleUri, null, true);
                mediaItemBuilder.setSubtitleConfigurations(Collections.singletonList(subtitle));
            }

            player.setMediaItem(mediaItemBuilder.build(), mPrefs.getPosition());
        }
    }

    //TODO ny
    public DataSource.Factory getEncryptedMediaSource(Context context, String path) {
        String password = EncryptUtil.getPasswordFromFileName(path);
        EncryptUtil.CTRnoPadding mCES = EncryptUtil.LevelCipherPackage(password);
        //  LevelCipherPackage(encryptLevel);
        DataSource.Factory dataSourceFactory;
        String useragent = Util.getUserAgent(context, "com.nytaiji.app");

        if (NyFileUtil.isOnline(path)) {
            //  dataSourceFactory = new OkHttpDataSourceFactory(mCES, new OkHttpClient(), useragent, null);
            dataSourceFactory = new OnlineEncryptedDataSourceFactory(mCES.cipher, mCES.secretKeySpec, mCES.ivParameterSpec, (Call.Factory) new OkHttpClient(), useragent, null);
            // Log.e(TAG, "---------------OnlineEncryptedDataSourceFactory");
            //online  cache before passed to decript
           /* String url = NyFileUtil.getPath(context, contentUri);
            cacheServer = getCacheServer(context);
            String proxyVideoUrl = cacheServer.getProxyUrl(url, true);
            contentUri = Uri.parse(proxyVideoUrl);*/
        } else {
            //  Log.e(TAG, "-----------EncryptedDataSourceFactory");
            dataSourceFactory = new EncryptedDataSourceFactory(context, mCES.cipher, mCES.secretKeySpec, mCES.ivParameterSpec, null);
        }
        return dataSourceFactory;
    }
}