package com.sturmen.xposed.keepchat;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.widget.Toast;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Keepchat implements IXposedHookLoadPackage {

	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if (!lpparam.packageName.equals("com.snapchat.android"))
			return;
		else
			XposedBridge.log("We are in Snapchat!");

		/*
		 * getImageBitmap(Context) hook
		 * The ReceivedSnap class has a method to load a Bitmap in preparation for viewing.
		 * This method returns said bitmap back so the application can display it.
		 * We hook this method to intercept the result and write it to the SD card.
		 * We then use the handily-provided Context to display a toast notification of success.
		 */
		findAndHookMethod("com.snapchat.android.model.ReceivedSnap", lpparam.classLoader, "getImageBitmap", Context.class, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				Bitmap myImage = (Bitmap) param.getResult();
				XposedBridge.log("Bitmap loaded.");
				//we find the root of where we can write to
				String root = Environment.getExternalStorageDirectory().toString();
				//we then go into our directory
				File myDir = new File(root + "/keepchat");
				XposedBridge.log("Saving to directory " + myDir.toString());
				//if /keepchat doesn't exist, make it.
				if (myDir.mkdirs())
					XposedBridge.log("Directory " + myDir.toString() + " was created.");
				String fname = "Image-"+ (System.currentTimeMillis() % 10000) + ".jpg";
				XposedBridge.log("Saving with filename " + fname);
				//construct a File object
				File file = new File (myDir, fname);
				//make sure it doesn't exist (should never execute due to timestamp in name)
				if (file.exists ())
					if (file.delete())
						XposedBridge.log("File " + fname + " overwritten.");
				try {
					//open a new outputstream for writing
					FileOutputStream out = new FileOutputStream(file);
					//use built-in bitmap function to turn it into a jpeg and write it to the stream
					myImage.compress(Bitmap.CompressFormat.JPEG, 90, out);
					//flush the stream to make sure it's done
					out.flush();
					//close it
					out.close();
					//construct a log entry
					CharSequence text = "Saved to " + myDir.toString() + "/" +  fname + "!";
					XposedBridge.log(text.toString());
					//get the original context to use for the toast
					Context context = (Context) param.args[0];
					//construct a toast notification telling the user it was successful
					Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
					//display the toast for the user
					toast.show();
					XposedBridge.log("Image Toast displayed successfully.");
				} catch (Exception e) {
					XposedBridge.log("Error occured while saving the file.");
					e.printStackTrace();
				}
				//return the original image to the original caller so the app can continue
				//TODO offer to return nag image so user has to go to sdcard to see snap and buy my app
			}
		});
		/*
		 * getVideoUri() hook
		 * The ReceivedSnap class treats videos a little differently.
		 * Videos are not their own object, so they can't be passed around.
		 * The Android system basically provides a VideoView for viewing videos,
		 * which you just provide it the location of the video and it does the rest.
		 * 
		 * Unsurprisingly, Snapchat makes use of this View.
		 * This method in the ReceivedSnap class gets the URI of the video
		 * in preparation for one of these VideoViews.
		 * We hook in, intercept the result (a String), then copy the bytes from
		 * that location to our SD directory. This results in a bit of a slowdown
		 * for the user, but luckily this takes place before they actually view it.
		 */
		findAndHookMethod("com.snapchat.android.model.ReceivedSnap", lpparam.classLoader, "getVideoUri", new XC_MethodHook() {
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				String videoUri = (String) param.getResult();
				XposedBridge.log("Video is at " + videoUri);
				//we construct a path for us to write to
				String root = Environment.getExternalStorageDirectory().toString();
				//and add our own directory
				File myDir = new File(root + "/keepchat");
				XposedBridge.log("Saving to directory " + myDir.toString());
				//we make the directory if it doesn't exist.
				if (myDir.mkdirs())
					XposedBridge.log("Directory " + myDir.toString() + " was created.");
				//construct the name for the new video. Uses timestamp so name will be unique
				String fname = "Video-"+ (System.currentTimeMillis() % 10000) + ".mp4";
				XposedBridge.log("Saving with filename " + fname);
				//construct a File object
				File file = new File (myDir, fname);
				//make sure it doesn't exist (should never execute due to timestamp in name)
				if (file.exists ())
					if (file.delete())
						XposedBridge.log("File " + fname + " overwritten.");
				try {
					//make a new input stream from the video URI
					FileInputStream in = new FileInputStream (new File(videoUri));
					//make a new output stream to write to
					FileOutputStream out = new FileOutputStream(file);
					//make a buffer we use for copying
					byte[] buf = new byte[1024];
					int len;
					//copy the file over using a while loop
					while ((len = in.read(buf)) > 0) {
						out.write(buf, 0, len);
					}
					//close the input stream
					in.close();
					//flush the output stream so we know it's finished
					out.flush();
					//and then close it
					out.close();
					//construct a log message
					CharSequence text = "Saved to " + myDir.toString() + "/" +  fname + " !";
					XposedBridge.log(text.toString());
				} catch (Exception e) {
					//if any exceptions are found, write to log
					XposedBridge.log("Error occured while saving the file.");
					e.printStackTrace();
				}
			}
		});
		/*
		 * showVideo() hook
		 * Because getVideoUri() does not handily provide a context,
		 * nor does its parent class (ReceivedSnap), we are unable to
		 * get the context necessary in order to display a notification.
		 * We "solve" this by cheating.
		 * This is an entirely separate hook that displays a Toast notification
		 * upon showing a video.
		 * It makes no checks whatsoever that the video saving was successful,
		 * it is merely for the benefit of the user to know that our hooks
		 * are working.
		 */
		findAndHookMethod("com.snapchat.android.FeedActivity", lpparam.classLoader, "showVideo", new XC_MethodHook() {
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
				Context context = ((Activity) param.thisObject).getApplicationContext();
				//construct a toast notification telling the user it was successful
				CharSequence text = "Saved video snap to SD card.";
				Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
				//display the toast for the user
				toast.show();
				XposedBridge.log("Video Toast displayed successfully.");
			}
		});
		/*
		 * wasScreenshotted() hook
		 * This is method is called to see if the Snap was screenshotted.
		 * We hook it to always return true, meaning that it was screenshotted.
		 * "False" would mean that it would always report that it was not screenshotted.
		 */
		findAndHookMethod("com.snapchat.android.model.ReceivedSnap", lpparam.classLoader, "wasScreenshotted", new XC_MethodReplacement() {

			@Override
			protected Object replaceHookedMethod(MethodHookParam param)
					throws Throwable {
				XposedBridge.log("Reporting screenshotted.");
				// the line
				return false;
			}
		});
	}

}
