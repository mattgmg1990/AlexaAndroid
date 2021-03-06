package com.willblaschko.android.alexa.audioplayer;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.PowerManager;
import android.util.Log;

import com.willblaschko.android.alexa.interfaces.AvsItem;
import com.willblaschko.android.alexa.interfaces.audioplayer.AvsPlayContentItem;
import com.willblaschko.android.alexa.interfaces.audioplayer.AvsPlayRemoteItem;
import com.willblaschko.android.alexa.interfaces.speechsynthesizer.AvsSpeakItem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A class that abstracts the Android MediaPlayer and adds additional functionality to handle AvsItems
 * as well as properly handle multiple callbacks--be care not to leak Activities by not removing the callback
 */
public class AlexaAudioPlayer {

    public static final String TAG = "AlexaAudioPlayer";

    private static AlexaAudioPlayer mInstance;

    private MediaPlayer mMediaPlayer;
    private Context mContext;
    private AvsItem mItem;
    private List<Callback> mCallbacks = new ArrayList<>();

    /**
     * Create our new AlexaAudioPlayer
     * @param context any context, we will get the application level to store locally
     */
    private AlexaAudioPlayer(Context context){
       mContext = context.getApplicationContext();
    }

    /**
     * Get a reference to the AlexaAudioPlayer instance, if it's null, we will create a new one
     * using the supplied context.
     * @param context any context, we will get the application level to store locally
     * @return our instance of the AlexaAudioPlayer
     */
    public static AlexaAudioPlayer getInstance(Context context){
        if(mInstance == null){
            mInstance = new AlexaAudioPlayer(context);
        }
        return mInstance;
    }

    /**
     * Return a reference to the MediaPlayer instance, if it does not exist,
     * then create it and configure it to our needs
     * @return Android native MediaPlayer
     */
    private MediaPlayer getMediaPlayer(){
        if(mMediaPlayer == null){
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
        }
        return mMediaPlayer;
    }

    /**
     * Add a callback to our AlexaAudioPlayer, this is added to our list of callbacks
     * @param callback Callback that listens to changes of player state
     */
    public void addCallback(Callback callback){
        if(!mCallbacks.contains(callback)){
            mCallbacks.add(callback);
        }
    }

    /**
     * Remove a callback from our AlexaAudioPlayer, this is removed from our list of callbacks
     * @param callback Callback that listens to changes of player state
     */
    public void removeCallback(Callback callback){
        mCallbacks.remove(callback);
    }

    /**
     * A helper function to play an AvsPlayContentItem, this is passed to play() and handled accordingly,
     * @param item a speak type item
     */
    public void playItem(AvsPlayContentItem item){
        play(item);
    }

    /**
     * A helper function to play an AvsSpeakItem, this is passed to play() and handled accordingly,
     * @param item a speak type item
     */
    public void playItem(AvsSpeakItem item){
        play(item);
    }

    /**
     * A helper function to play an AvsPlayRemoteItem, this is passed to play() and handled accordingly,
     * @param item a play type item, usually a url
     */
    public void playItem(AvsPlayRemoteItem item){
        play(item);
    }

    /**
     * Request our MediaPlayer to play an item, if it's an AvsPlayRemoteItem (url, usually), we set that url as our data source for the MediaPlayer
     * if it's an AvsSpeakItem, then we write the raw audio to a file and then read it back using the MediaPlayer
     * @param item
     */
    private void play(AvsItem item){
        if(isPlaying()){
            Log.w(TAG, "Already playing an item, did you mean to play another?");
        }
        mItem = item;
        if(getMediaPlayer().isPlaying()){
            //if we're playing, stop playing before we continue
            getMediaPlayer().stop();
        }
        if(mItem instanceof AvsPlayRemoteItem){
            //cast our item for easy access
            AvsPlayRemoteItem playItem = (AvsPlayRemoteItem) item;
            try {
                //reset our player
                getMediaPlayer().reset();
                //set stream
                getMediaPlayer().setAudioStreamType(AudioManager.STREAM_MUSIC);
                //play new url
                getMediaPlayer().setDataSource(playItem.getUrl());
            } catch (IOException e) {
                e.printStackTrace();
                //bubble up our error
                bubbleUpError(e);
            }
        }else if(mItem instanceof AvsPlayContentItem){
            //cast our item for easy access
            AvsPlayContentItem playItem = (AvsPlayContentItem) item;
            try {
                //reset our player
                getMediaPlayer().reset();
                //set stream
                getMediaPlayer().setAudioStreamType(AudioManager.STREAM_MUSIC);
                //play new url
                getMediaPlayer().setDataSource(mContext, playItem.getUri());
            } catch (IOException e) {
                e.printStackTrace();
                //bubble up our error
                bubbleUpError(e);
            } catch (IllegalStateException e){
                e.printStackTrace();
                //bubble up our error
                bubbleUpError(e);
            }
        }else if(mItem instanceof AvsSpeakItem){
            //cast our item for easy access
            AvsSpeakItem playItem = (AvsSpeakItem) item;
            //write out our raw audio data to a file
            File path=new File(mContext.getCacheDir()+"/playfile.3gp");
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(path);
                fos.write(playItem.getAudio());
                fos.close();
                //reset our player
                getMediaPlayer().reset();
                //play our newly-written file
                getMediaPlayer().setDataSource(mContext.getCacheDir() + "/playfile.3gp");
            } catch (IOException e) {
                e.printStackTrace();
                //bubble up our error
                bubbleUpError(e);
            }

        }
        //prepare our player, this will start once prepared because of mPreparedListener
        try {
            getMediaPlayer().prepareAsync();
        }catch (IllegalStateException e){
            bubbleUpError(e);
        }
    }

    /**
     * Check whether our MediaPlayer is currently playing
     * @return true playing, false not
     */
    public boolean isPlaying(){
        return getMediaPlayer().isPlaying();
    }

    /**
     * A helper function to pause the MediaPlayer
     */
    public void pause(){
        getMediaPlayer().pause();
    }

    /**
     * A helper function to play the MediaPlayer
     */
    public void play(){
        getMediaPlayer().start();
    }

    /**
     * A helper function to stop the MediaPlayer
     */
    public void stop(){
        getMediaPlayer().stop();
    }

    /**
     * A helper function to release the media player and remove it from memory
     */
    public void release(){
        if(mMediaPlayer != null){
            if(mMediaPlayer.isPlaying()){
                mMediaPlayer.stop();
            }
            mMediaPlayer.reset();
            mMediaPlayer.release();
        }
        mMediaPlayer = null;
    }

    /**
     * A callback to keep track of the state of the MediaPlayer and various AvsItem states
     */
    public interface Callback{
        void playerPrepared(AvsItem pendingItem);
        void itemComplete(AvsItem completedItem);
        boolean playerError(int what, int extra);
        void dataError(Exception e);
    }

    /**
     * Pass our Exception to all the Callbacks, handle it at the top level
     * @param e the thrown exception
     */
    private void bubbleUpError(Exception e){
        for(Callback callback: mCallbacks){
            callback.dataError(e);
        }
    }

    /**
     * Pass our MediaPlayer completion state to all the Callbacks, handle it at the top level
     */
    private MediaPlayer.OnCompletionListener mCompletionListener = new MediaPlayer.OnCompletionListener() {
        @Override
        public void onCompletion(MediaPlayer mp) {
            for(Callback callback: mCallbacks){
                callback.itemComplete(mItem);
            }
        }
    };

    /**
     * Pass our MediaPlayer prepared state to all the Callbacks, handle it at the top level
     */
    private MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
        @Override
        public void onPrepared(MediaPlayer mp) {
            for(Callback callback: mCallbacks){
                callback.playerPrepared(mItem);
            }
            mMediaPlayer.start();
        }
    };

    /**
     * Pass our MediaPlayer error state to all the Callbacks, handle it at the top level
     */
    private MediaPlayer.OnErrorListener mErrorListener = new MediaPlayer.OnErrorListener() {
        @Override
        public boolean onError(MediaPlayer mp, int what, int extra) {
            for(Callback callback: mCallbacks){
                boolean response = callback.playerError(what, extra);
                if(response){
                    return response;
                }
            }
            return false;
        }
    };
}
