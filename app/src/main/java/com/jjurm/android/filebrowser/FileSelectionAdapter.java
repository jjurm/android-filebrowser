package com.jjurm.android.filebrowser;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A subclass of ArrayAdapter designed to be used with FileWrapper. Allows selection of entries and
 * is also used as a listener for multi-choice mode (by implementing AbsListView.MultiChoiceModeListener)
 */
public class FileSelectionAdapter extends ArrayAdapter<FileWrapper> implements AbsListView.MultiChoiceModeListener {

    private MainActivity mActivity;
    private List<FileWrapper> mObjects;

    /**
     * a set containing the selected items
     */
    private Set<Integer> mSelection = new HashSet<>();

    public FileSelectionAdapter(MainActivity activity, int resource, int textViewResourceId,
                                List<FileWrapper> objects) {
        super(activity, resource, textViewResourceId, objects);
        mActivity = activity;
        mObjects = objects;
    }

    @Override
    public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
        // update the selection set
        if (checked)
            mSelection.add(position);
        else
            mSelection.remove(position);
        mode.setTitle(mActivity.getResources().getString(R.string.n_selected, mSelection.size()));
        notifyDataSetChanged();
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.menu_action_mode, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(final ActionMode mode, MenuItem item) {
        // collect the files to delete
        final File[] filesToDelete = new File[mSelection.size()];
        int i = 0;
        for (Integer index : mSelection) {
            filesToDelete[i++] = mObjects.get(index).getFile();
        }

        // ask user to confirm the action and then delete the files and finish the action mode
        new AlertDialog.Builder(mActivity)
                .setIcon(R.drawable.ic_warning)
                .setTitle(mActivity.getResources().getQuantityString(R.plurals.delete_n_files,
                        filesToDelete.length, filesToDelete.length))
                .setMessage(mActivity.getResources().getQuantityString(
                        R.plurals.are_you_sure_to_delete, filesToDelete.length))
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mActivity.tryToDeleteFiles(filesToDelete);
                        mode.finish();
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();

        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mSelection.clear();
    }

    private boolean isSelected(int position) {
        return mSelection.contains(position);
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        View v = super.getView(position, convertView, parent);

        // check if the item is a file or a directory
        ImageView imageView = (ImageView) v.findViewById(R.id.imageView);
        if (mObjects.get(position).isDirectory()) {
            imageView.setImageResource(R.drawable.ic_folder);
        } else {
            imageView.setImageResource(R.drawable.ic_file);
        }

        // check if the item is selected
        if (isSelected(position)) {
            v.setBackgroundColor(ContextCompat.getColor(mActivity, R.color.colorAccent));
        } else {
            v.setBackgroundColor(ContextCompat.getColor(mActivity, android.R.color.white));
        }

        return v;
    }

}
