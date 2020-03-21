package org.radiocells.unifiedNlp.utils;
/*
 * Gregory Shpitalnik
 * http://www.codeproject.com/Articles/547636/Android-Ready-to-use-simple-directory-chooser-dial?msg=4923192#xx4923192xx
 */

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnKeyListener;
import android.os.Environment;
import android.text.Editable;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.radiocells.unifiedNlp.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DirectoryChooserDialog {
    private static final String TAG = DirectoryChooserDialog.class.getSimpleName();
    private boolean mIsNewFolderEnabled = true;
    private String mSdcardDirectory;
    private Context mContext;
    private TextView mTitleView;
    private TextView mSubtitleView;

    private String mDir = "";
    private List<String> mSubdirs = null;
    private ChosenDirectoryListener mChosenDirectoryListener;
    private ArrayAdapter<String> mListAdapter = null;

    public DirectoryChooserDialog(Context context, ChosenDirectoryListener chosenDirectoryListener) {
        mContext = context;
        mSdcardDirectory = new File(Environment.getExternalStorageDirectory().getAbsolutePath()).getAbsolutePath();
        mChosenDirectoryListener = chosenDirectoryListener;
    }

    public boolean getNewFolderEnabled() {
        return mIsNewFolderEnabled;
    }

    /**
     * enable/disable new folder button
     */
    public void setNewFolderEnabled(boolean isNewFolderEnabled) {
        mIsNewFolderEnabled = isNewFolderEnabled;
    }

    /**
     * chooseDirectory() - load directory chooser dialog for initial default sdcard directory
     */
    public void chooseDirectory() {
        // Initial directory is sdcard directory
        chooseDirectory(mSdcardDirectory);
    }

    /**
     * chooseDirectory(String dir) - load directory chooser dialog for initial input 'dir' directory
     */
    public void chooseDirectory(String dir) {
        File dirFile = new File(dir);
        if (!dirFile.exists() || !dirFile.isDirectory()) {
            dir = mSdcardDirectory;
        }

        dir = new File(dir).getAbsolutePath();

        mDir = dir;
        mSubdirs = getDirectories(dir);

        class DirectoryOnClickListener implements DialogInterface.OnClickListener {
            public void onClick(DialogInterface dialog, int item) {
                // handle folder up clicks
                if (((AlertDialog) dialog).getListView().getAdapter().getItem(item).equals("..")) {
                    // handle '..' (directory up) clicks
                    mDir = mDir.substring(0, mDir.lastIndexOf("/"));
                } else {
                    // otherwise descend into sub-directory
                    mDir += "/" + ((AlertDialog) dialog).getListView().getAdapter().getItem(item);
                }
                updateDirectory();
            }
        }

        AlertDialog.Builder dialogBuilder =
                createDirectoryChooserDialog("Select folder", dir, mSubdirs, new DirectoryOnClickListener());

        dialogBuilder.setPositiveButton("OK", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Current directory chosen
                if (mChosenDirectoryListener != null) {
                    // Call registered listener supplied with the chosen directory
                    mChosenDirectoryListener.onChosenDir(mDir);
                }
            }
        }).setNegativeButton("Cancel", null);

        final AlertDialog dirsDialog = dialogBuilder.create();

        dirsDialog.setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN) {
                    // Back button pressed
                    if (mDir.equals(mSdcardDirectory)) {
                        // The very top level directory, do nothing
                        return false;
                    } else {
                        // Navigate back to an upper directory
                        mDir = new File(mDir).getParent();
                        updateDirectory();
                    }

                    return true;
                } else {
                    return false;
                }
            }
        });

        // Show directory chooser dialog
        dirsDialog.show();
    }

    private boolean createSubDir(String newDir) {
        File newDirFile = new File(newDir);
        if (!newDirFile.exists()) {
            return newDirFile.mkdir();
        }

        return false;
    }

    private List<String> getDirectories(String dir) {
        List<String> dirs = new ArrayList<>();
        dirs.add("..");
        try {
            File dirFile = new File(dir);
            if (!dirFile.exists() || !dirFile.isDirectory()) {
                return dirs;
            }

            for (File file : dirFile.listFiles()) {
                if (file.isDirectory()) {
                    dirs.add(file.getName());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error listing directory");
        }

        Collections.sort(dirs, new Comparator<String>() {
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        });

        return dirs;
    }

    private AlertDialog.Builder createDirectoryChooserDialog(String title, String subtitle, List<String> listItems,
                                                             DialogInterface.OnClickListener onClickListener) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(mContext);

        // Create custom view for AlertDialog title containing
        // current directory TextView and possible 'New folder' button.
        // Current directory TextView allows long directory path to be wrapped to multiple lines.
        LinearLayout titleLayout = new LinearLayout(mContext);
        titleLayout.setOrientation(LinearLayout.VERTICAL);

        mTitleView = new TextView(mContext);
        mTitleView.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
        mTitleView.setTextAppearance(mContext, android.R.style.TextAppearance_Large);
        mTitleView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        mTitleView.setText(title);

        mSubtitleView = new TextView(mContext);
        mSubtitleView.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
        mSubtitleView.setTextAppearance(mContext, android.R.style.TextAppearance_Large);
        mSubtitleView.setGravity(Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        mSubtitleView.setTextAppearance(mContext, android.R.style.TextAppearance_Small);
        mSubtitleView.setText(subtitle);

        Button newDirButton = new Button(mContext);
        newDirButton.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
        newDirButton.setText(R.string.new_folder);
        newDirButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final EditText input = new EditText(mContext);

                // Show new folder name input dialog
                new AlertDialog.Builder(mContext).
                        setTitle("New folder name").
                        setView(input).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Editable newDir = input.getText();
                        String newDirName = newDir.toString();
                        // Create new directory
                        if (createSubDir(mDir + "/" + newDirName)) {
                            // Navigate into the new directory
                            mDir += "/" + newDirName;
                            updateDirectory();
                        } else {
                            Toast.makeText(
                                    mContext, "Failed to create '" + newDirName +
                                            "' folder", Toast.LENGTH_SHORT).show();
                        }
                    }
                }).setNegativeButton("Cancel", null).show();
            }
        });

        if (!mIsNewFolderEnabled) {
            newDirButton.setVisibility(View.GONE);
        }

        titleLayout.addView(mTitleView);
        titleLayout.addView(mSubtitleView);
        titleLayout.addView(newDirButton);

        dialogBuilder.setCustomTitle(titleLayout);

        mListAdapter = createListAdapter(listItems);

        dialogBuilder.setSingleChoiceItems(mListAdapter, -1, onClickListener);
        dialogBuilder.setCancelable(false);

        return dialogBuilder;
    }

    private void updateDirectory() {
        mSubdirs.clear();
        mSubdirs.addAll(getDirectories(mDir));
        mSubtitleView.setText(mDir);

        mListAdapter.notifyDataSetChanged();
    }

    private ArrayAdapter<String> createListAdapter(List<String> items) {
        return new ArrayAdapter<String>(mContext,
                android.R.layout.select_dialog_item, android.R.id.text1, items) {
            @NonNull
            @Override
            public View getView(int position, View convertView,
                                @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);

                if (v instanceof TextView) {
                    // Enable list item (directory) text wrapping
                    TextView tv = (TextView) v;
                    tv.getLayoutParams().height = LayoutParams.WRAP_CONTENT;
                    tv.setEllipsize(null);
                }
                return v;
            }
        };
    }

    /*
     * Callback interface for selected directory
     */
    public interface ChosenDirectoryListener {
        void onChosenDir(String chosenDir);
    }
}