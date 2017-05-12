package com.jjurm.android.filebrowser;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The main activity.
 */

public class MainActivity extends AppCompatActivity {

    // IDs for permission requests
    public static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 1;
    public static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 2;

    // types of animation of the file list
    public static final int ANIM_LEVEL_UP = 1;
    public static final int ANIM_LEVEL_DOWN = -1;
    public static final int ANIM_NONE = 0;

    // what permissions have already been requested
    Set<Integer> mPermissionsRequested = new HashSet<>();

    // files to delete after a write permission is granted
    File[] mFilesToDelete = null;

    // the directory to start with
    File defaultPath;

    // current position
    File mCurrentPath;

    // executor for tasks on a separate thread
    ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // initialize action bar
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(R.string.app_title);
        actionBar.setIcon(R.mipmap.ic_actionbar);
        actionBar.setDisplayShowHomeEnabled(true);

        // determine default directory
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String pathname = sharedPref.getString(SettingsActivity.KEY_PREF_DEFAULT_DIRECTORY, "");
        if (pathname.equals("")) {
            defaultPath = Environment.getExternalStorageDirectory();
        } else {
            defaultPath = new File(pathname);
        }
        mCurrentPath = defaultPath;

        // load the directory at startup
        refreshDir(ANIM_NONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // inflate the action bar menu
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                // refresh the list of files
                refreshDir(ANIM_NONE);
                return true;
            case R.id.action_settings:
                // open settings
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onBackPressed() {
        // determine if the app should go up or close
        File parent = mCurrentPath.getParentFile();
        if (parent == null || mCurrentPath.equals(defaultPath)) {
            // exit activity
            finish();
        } else {
            // go one level up
            mCurrentPath = parent;
            refreshDir(ANIM_LEVEL_DOWN);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE:
                // refresh the view regardless of the result
                refreshDir(ANIM_NONE);
                break;
            case PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE:
                // check if the permission was granted
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // delete files that were requested to be deleted
                    if (mFilesToDelete != null) {
                        for (File f : mFilesToDelete)
                            deleteRecursive(f);
                        refreshDir(ANIM_NONE);
                    }
                }
                break;
        }
    }

    /**
     * Move one level of directories further
     *
     * @param newPath the path of the child directory
     */
    void changeCurrentPath(File newPath) {
        mCurrentPath = newPath;
        refreshDir(ANIM_LEVEL_UP);
    }

    /**
     * Put the new fragment into the frame layout
     *
     * @param newFragment new fragment to change to
     * @param anim        either ANIM_LEVEL_UP, ANIM_LEVEL_DOWN or ANIM_NONE
     */
    public void replaceFragment(Fragment newFragment, int anim) {
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        switch (anim) {
            case ANIM_LEVEL_UP:
                fragmentTransaction.setCustomAnimations(R.anim.enter_from_right, R.anim.exit_to_left);
                break;
            case ANIM_LEVEL_DOWN:
                fragmentTransaction.setCustomAnimations(R.anim.enter_from_left, R.anim.exit_to_right);
                break;
            default:
        }
        fragmentTransaction.replace(R.id.fragment_container, newFragment).commit();
    }

    /**
     * Check for necessary permissions, load the files in the current directory and move to a new
     * fragment
     *
     * @param anim either ANIM_LEVEL_UP, ANIM_LEVEL_DOWN or ANIM_NONE
     */
    private void refreshDir(final int anim) {
        // check read permission
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {

            if (!mPermissionsRequested.contains(PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE)) {
                // request the permission
                mPermissionsRequested.add(PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
            } else {
                // last permission request was unsuccessful
                MessageFragment messageFragment = new MessageFragment();
                messageFragment.setMessage(getString(R.string.missing_read_external_storage_permission));
                replaceFragment(messageFragment, anim);
            }
            return;
        }

        // check if the external storage is mounted
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            MessageFragment messageFragment = new MessageFragment();
            messageFragment.setMessage(getString(R.string.external_storage_not_mounted));
            replaceFragment(messageFragment, anim);
            return;
        }

        // show the path in the subtitle
        getSupportActionBar().setSubtitle(mCurrentPath.getAbsolutePath());

        // list files in a separate thread
        executor.submit(new Runnable() {
            @Override
            public void run() {

                // list files
                File[] files = mCurrentPath.listFiles();
                if (files == null) {
                    MessageFragment messageFragment = new MessageFragment();
                    messageFragment.setMessage(getString(R.string.cant_read_dir));
                    replaceFragment(messageFragment, anim);
                } else if (files.length == 0) {
                    MessageFragment messageFragment = new MessageFragment();
                    messageFragment.setMessage(getString(R.string.empty_directory));
                    replaceFragment(messageFragment, anim);
                } else {
                    // sort files by names, putting directories first
                    Arrays.sort(files, new Comparator<File>() {
                        @Override
                        public int compare(File o1, File o2) {
                            int b1 = o1.isDirectory() ? 1 : 0;
                            int b2 = o2.isDirectory() ? 1 : 0;
                            int dirComp = b2 - b1;
                            if (dirComp != 0)
                                return dirComp;
                            else return o1.getName().compareToIgnoreCase(o2.getName());
                        }
                    });
                    // update the frame layout with new fragment
                    FilesFragment filesFragment = new FilesFragment();
                    filesFragment.updateList(files);
                    replaceFragment(filesFragment, anim);
                }

            }
        });
    }

    /**
     * Check for write permissions and attempt to delete the files.
     *
     * @param filesToDelete array of files or folders to delete
     */
    public void tryToDeleteFiles(File[] filesToDelete) {
        // check write permission
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {

            if (!mPermissionsRequested.contains(PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE)) {
                // request the permission
                mPermissionsRequested.add(PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            } else {
                // last permission request was unsuccessful
                Toast.makeText(this, getString(R.string.missing_write_external_storage_permission),
                        Toast.LENGTH_LONG).show();
            }

        } else {
            // delete the files
            for (File f : filesToDelete)
                deleteRecursive(f);
            refreshDir(ANIM_NONE);
        }
    }

    /**
     * If the path is a directory, it recursively deletes all files in it. Then it deletes the path
     * itself.
     *
     * @param fileOrDirectory path to delete
     */
    public void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory())
            for (File child : fileOrDirectory.listFiles())
                deleteRecursive(child);

        fileOrDirectory.delete();
    }

    /**
     * Fragment to use for listing the files
     */
    public static class FilesFragment extends Fragment {

        List<FileWrapper> wrappers = new ArrayList<>();
        FileSelectionAdapter mAdapter;
        AbsListView mListView;

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.files_fragment, container, false);

            // create and configure the AbsListView
            mListView = (AbsListView) v.findViewById(R.id.listview);
            mAdapter = new FileSelectionAdapter(
                    (MainActivity) getActivity(), R.layout.list_item, android.R.id.text1, wrappers);
            mListView.setMultiChoiceModeListener(mAdapter);
            mListView.setAdapter(mAdapter);
            mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    File path = mAdapter.getItem(position).getFile();
                    if (path.isDirectory()) {
                        // clicked entry is a directory, navigate to it
                        ((MainActivity) getActivity()).changeCurrentPath(path);
                    } else {
                        // clicked entry is a file
                        openFile(path);
                    }
                }
            });

            return v;
        }

        /**
         * Update the files listed
         *
         * @param files new list of files to show
         */
        public void updateList(File[] files) {
            wrappers.clear();
            for (File file : files) {
                wrappers.add(new FileWrapper(file));
            }
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        }

        /**
         * Try to find appropriate handler for the file chosen, grant it permission to access the
         * file and start the intent
         *
         * @param path path to the file
         */
        private void openFile(File path) {
            // guess the content type and open the file
            Context context = getActivity();
            Intent newIntent = new Intent();
            newIntent.setAction(Intent.ACTION_VIEW);
            String mimeType = URLConnection.guessContentTypeFromName(path.getName());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                newIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Uri uri = FileProvider.getUriForFile(context,
                        context.getApplicationContext().getPackageName() + ".provider", path);
                newIntent.setDataAndType(uri, mimeType);

                List<ResolveInfo> resInfoList = context.getPackageManager()
                        .queryIntentActivities(newIntent, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolveInfo : resInfoList) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    context.grantUriPermission(packageName, uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            } else {
                newIntent.setDataAndType(Uri.fromFile(path), mimeType);
            }
            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(newIntent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(context, R.string.no_handler, Toast.LENGTH_LONG).show();
            }
        }
    }


    /**
     * Fragment to use when displaying an information about the current directory.
     */
    public static class MessageFragment extends Fragment {

        String mMessage = "";
        TextView mTextView;

        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            RelativeLayout relativeLayout = new RelativeLayout(getActivity());

            mTextView = new TextView(getActivity());
            mTextView.setText(mMessage);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                    RelativeLayout.LayoutParams.WRAP_CONTENT,
                    RelativeLayout.LayoutParams.WRAP_CONTENT);
            lp.addRule(RelativeLayout.CENTER_IN_PARENT);
            mTextView.setLayoutParams(lp);

            relativeLayout.addView(mTextView);
            return relativeLayout;
        }

        /**
         * Update the shown message
         *
         * @param message new message to show
         */
        public void setMessage(String message) {
            mMessage = message;
            if (mTextView != null)
                mTextView.setText(message);
        }

    }

}
