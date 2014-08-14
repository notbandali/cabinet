package com.afollestad.cabinet.file.root;

import android.app.Activity;
import android.util.Log;

import com.afollestad.cabinet.R;
import com.afollestad.cabinet.file.base.File;
import com.afollestad.cabinet.file.base.FileFilter;
import com.afollestad.cabinet.sftp.SftpClient;
import com.afollestad.cabinet.utils.Utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import eu.chainfire.libsuperuser.Shell;

public class RootFile extends File {

    public RootFile(Activity context) {
        super(context, null);
    }

    public RootFile(Activity context, String path, String name) {
        super(context, path + "/" + name);
        setPath(path + "/" + name);
    }

    public String permissions;
    public String owner;
    public String creator;
    public long size = -1;
    public String date;
    public String time;
    public String originalName;

    @Override
    public boolean isHidden() {
        return getName().startsWith(".");
    }

    @Override
    public File getParent() {
        java.io.File mFile = new java.io.File(getPath());
        return new RootFile(getContext(), mFile.getParent(), mFile.getName());
    }

    @Override
    public void createFile(final SftpClient.CompletionCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    RootFile.runAsRoot("touch \"" + getPath() + "\"");
                    callback.onComplete();
                } catch (Exception e) {
                    e.printStackTrace();
                    callback.onError(e);
                }
            }
        }).start();
    }

    @Override
    public void mkdir(final SftpClient.CompletionCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    RootFile.runAsRoot("mkdir -P \"" + getPath() + "\"");
                    callback.onComplete();
                } catch (Exception e) {
                    e.printStackTrace();
                    callback.onError(e);
                }
            }
        }).start();
    }

    @Override
    public void rename(File newFile, final SftpClient.CompletionCallback callback) {
        Utils.checkDuplicates(getContext(), newFile, new Utils.DuplicateCheckResult() {
            @Override
            public void onResult(final File newFile) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            RootFile.runAsRoot("mv -f \"" + getPath() + "\" \"" + newFile.getPath() + "\"");
                            getContext().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    setPath(newFile.getPath());
                                    callback.onComplete();
                                    notifyMediaScannerService(newFile);
                                }
                            });
                        } catch (final Exception e) {
                            e.printStackTrace();
                            getContext().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Utils.showErrorDialog(getContext(), R.string.failed_rename_file, e);
                                    callback.onError(null);
                                }
                            });
                        }
                    }
                }).start();
            }
        });
    }

    @Override
    public void copy(final File dest, final SftpClient.FileCallback callback) {
        Utils.checkDuplicates(getContext(), dest, new Utils.DuplicateCheckResult() {
            @Override
            public void onResult(final File dest) {
                try {
                    RootFile.runAsRoot("cp -R \"" + getPath() + "\" \"" + dest.getPath() + "\"");
                    getContext().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            callback.onComplete(dest);
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    getContext().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Utils.showErrorDialog(getContext(), R.string.failed_copy_file, e);
                            callback.onError(null);
                        }
                    });
                }
            }
        });
    }

    @Override
    public void delete(final SftpClient.CompletionCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    deleteSync();
                    getContext().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) callback.onComplete();
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    getContext().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Utils.showErrorDialog(getContext(), R.string.failed_delete_file, e);
                            if (callback != null) callback.onError(null);
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    public boolean deleteSync() throws Exception {
        RootFile.runAsRoot("rm -rf \"" + getPath() + "\"");
        return true;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        return size == -1;
    }

    @Override
    public void exists(final BooleanCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final boolean exists = existsSync();
                    getContext().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (callback != null) callback.onComplete(exists);
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    getContext().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Utils.showErrorDialog(getContext(), R.string.error, e);
                            if (callback != null) callback.onError(null);
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    public boolean existsSync() throws Exception {
        String cmd;
        if (isDirectory()) {
            cmd = "[ -d \"" + getPath() + "\" ] && echo \"1\" || echo \"0\"";
        } else {
            cmd = "[ -f \"" + getPath() + "\" ] && echo \"1\" || echo \"0\"";
        }
        return Integer.parseInt(RootFile.runAsRoot(cmd).get(0)) == 1;
    }

    @Override
    public long length() {
        return size;
    }

    @Override
    public void listFiles(final boolean includeHidden, final ArrayCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<File> results = listFilesSync(includeHidden);
                    getContext().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            callback.onComplete(results != null ? results.toArray(new File[results.size()]) : null);
                        }
                    });
                } catch (final Exception e) {
                    e.printStackTrace();
                    getContext().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError(e);
                        }
                    });
                }
            }
        }).start();
    }

    @Override
    public List<File> listFilesSync(boolean includeHidden, FileFilter filter) throws Exception {
        List<String> response = RootFile.runAsRoot("ls -l \"" + getPath() + "\"");
        return LsParser.parse(getContext(), getPath(), response, filter, includeHidden).getFiles();
    }

    @Override
    public long lastModified() {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:ss", Locale.getDefault()).parse(date + " " + time).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static List<String> runAsRoot(String command) throws Exception {
        Log.v("Cabinet-SU", command);
        boolean suAvailable = Shell.SU.available();
        if (!suAvailable) throw new Exception("Superuser is not available.");
        return Shell.SU.run(new String[]{
                "mount -o remount,rw /",
                command
        });
    }
}
