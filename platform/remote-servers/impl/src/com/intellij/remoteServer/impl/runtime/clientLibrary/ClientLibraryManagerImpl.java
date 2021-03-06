package com.intellij.remoteServer.impl.runtime.clientLibrary;

import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.remoteServer.runtime.clientLibrary.ClientLibraryDescription;
import com.intellij.remoteServer.runtime.clientLibrary.ClientLibraryManager;
import com.intellij.util.EventDispatcher;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.download.DownloadableFileSetDescription;
import com.intellij.util.download.DownloadableFileSetVersions;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: michael.golubev
 */
@State(
  name = "RemoteServerClientLibraries",
  storages = {
    @Storage(file = StoragePathMacros.APP_CONFIG + "/remote-server-client-libraries.xml")
  }
)
public class ClientLibraryManagerImpl extends ClientLibraryManager implements PersistentStateComponent<ClientLibraryManagerImpl.State> {
  private static final Logger LOG = Logger.getInstance(ClientLibraryManagerImpl.class);
  private Map<String, List<File>> myFiles = new LinkedHashMap<String, List<File>>();

  private EventDispatcher<CloudClientLibraryManagerListener> myEventDispatcher
    = EventDispatcher.create(CloudClientLibraryManagerListener.class);


  @Override
  public void addListener(@NotNull CloudClientLibraryManagerListener listener, @NotNull Disposable disposable) {
    myEventDispatcher.addListener(listener, disposable);
  }

  public State getState() {
    State result = new State();
    for (Map.Entry<String, List<File>> entry : myFiles.entrySet()) {
      DownloadedLibraryState libraryState = new DownloadedLibraryState();
      libraryState.myId = entry.getKey();
      for (File file : entry.getValue()) {
        libraryState.myPaths.add(FileUtil.toSystemIndependentName(file.getAbsolutePath()));
      }
      result.myLibraries.add(libraryState);
    }
    return result;
  }

  @Override
  public void loadState(State state) {
    myFiles = new HashMap<String, List<File>>();
    for (DownloadedLibraryState libraryState : state.myLibraries) {
      List<File> files = new ArrayList<File>();
      for (String path : libraryState.myPaths) {
        files.add(new File(path));
      }
      myFiles.put(libraryState.myId, files);
    }
  }

  @Override
  public boolean isDownloaded(@NotNull ClientLibraryDescription description) {
    return !getExistentFiles(description).isEmpty();
  }

  @NotNull
  private List<File> getExistentFiles(ClientLibraryDescription description) {
    List<File> files = myFiles.get(description.getId());
    if (files == null) {
      return Collections.emptyList();
    }
    List<File> existentFiles = new ArrayList<File>();
    for (File file : files) {
      if (file.exists()) {
        existentFiles.add(file);
      }
    }
    return existentFiles;
  }

  @Override
  public void checkConfiguration(@NotNull final ClientLibraryDescription description, final @Nullable Project project,
                                 final @Nullable JComponent component) throws RuntimeConfigurationError {
    if (!isDownloaded(description)) {
      throw new RuntimeConfigurationError("Client libraries were not downloaded", new Runnable() {
        @Override
        public void run() {
          download(description, project, component);
        }
      });
    }
  }

  private static File getStoreDirectory(ClientLibraryDescription description) {
    return new File(PathManager.getSystemPath(), "remote-server-libraries/" + description.getId());
  }

  @NotNull
  @Override
  public List<File> getLibraries(@NotNull ClientLibraryDescription description) {
    File[] files = getStoreDirectory(description).listFiles();
    return files == null ? Collections.<File>emptyList() : Arrays.asList(files);
  }

  @Override
  public void download(@NotNull final ClientLibraryDescription libraryDescription, @Nullable Project project, @Nullable JComponent component) {
    final Ref<IOException> exc = Ref.create(null);
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      new Runnable() {
        @Override
        public void run() {
          try {
            download(libraryDescription);
          }
          catch (IOException e) {
            exc.set(e);
          }
        }
      }, "Downloading Client Libraries", false, project, component);
    if (exc.isNull()) {
      myEventDispatcher.getMulticaster().downloaded();
    }
    else {
      LOG.info(exc.get());
    }
  }

  @NotNull
  @Override
  public List<File> download(@NotNull final ClientLibraryDescription libraryDescription) throws IOException {
    List<File> existentFiles = getExistentFiles(libraryDescription);
    if (!existentFiles.isEmpty()) {
      return existentFiles;
    }

    final DownloadableFileService downloadService = DownloadableFileService.getInstance();

    final Ref<DownloadableFileSetDescription> descriptionRef = new Ref<DownloadableFileSetDescription>();
    URL versionsUrl = libraryDescription.getDescriptionUrl();
    final DownloadableFileSetVersions<DownloadableFileSetDescription> versions = downloadService.createFileSetVersions(null, versionsUrl);

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    versions.fetchVersions(new DownloadableFileSetVersions.FileSetVersionsCallback<DownloadableFileSetDescription>() {
      @Override
      public void onSuccess(@NotNull List<? extends DownloadableFileSetDescription> versions) {
        if (!versions.isEmpty()) {
          descriptionRef.set(versions.get(0));
        }
        semaphore.up();
      }

      @Override
      public void onError(@NotNull String errorMessage) {
        LOG.error(errorMessage);
        semaphore.up();
      }
    });
    semaphore.waitFor();

    final DownloadableFileSetDescription description = descriptionRef.get();
    if (description == null) {
      throw new IOException("No client library versions loaded");
    }

    final Ref<List<Pair<File,DownloadableFileDescription>>> downloaded = Ref.create(null);
    final Ref<IOException> exception = Ref.create(null);
    semaphore.down();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        new Task.Backgroundable(null, "Downloading Client Libraries", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            try {
              downloaded.set(downloadService.createDownloader(description).download(getStoreDirectory(libraryDescription)));
            }
            catch (IOException e) {
              exception.set(e);
            }
            finally {
              semaphore.up();
            }
          }
        }.queue();
      }
    });
    semaphore.waitFor();

    if (!exception.isNull()) {
      throw exception.get();
    }
    if (downloaded.isNull()) {
      throw new IOException("Downloading client libraries cancelled");
    }

    List<File> files = myFiles.get(libraryDescription.getId());
    if (files == null) {
      files = new ArrayList<File>();
      myFiles.put(libraryDescription.getId(), files);
    }
    for (Pair<File, DownloadableFileDescription> pair : downloaded.get()) {
      files.add(pair.getFirst());
    }

    myEventDispatcher.getMulticaster().downloaded();

    return files;
  }

  @Tag("client-library")
  public static class DownloadedLibraryState {
    @Attribute("id")
    public String myId;

    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false, elementTag = "file", elementValueAttribute = "path")
    public List<String> myPaths = new ArrayList<String>();
  }

  public static class State {
    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false)
    public List<DownloadedLibraryState> myLibraries = new ArrayList<DownloadedLibraryState>();
  }
}
