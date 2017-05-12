package com.jjurm.android.filebrowser;

import android.support.annotation.NonNull;

import java.io.File;

/**
 * Simple class which helps a File and implements toString() as expected
 */
class FileWrapper {

    private File file;
    private boolean isDirectory;

    public FileWrapper(@NonNull File file) {
        this.file = file;
        isDirectory = file.isDirectory();
    }

    public File getFile() {
        return file;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    @NonNull
    @Override
    public String toString() {
        return file.getName() + (isDirectory ? "/" : "");
    }

}
