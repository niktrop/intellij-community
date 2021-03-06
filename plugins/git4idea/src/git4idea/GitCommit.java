/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea;

import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.impl.VcsFullCommitDetailsImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a Git commit with its meta information (hash, author, message, etc.), its parents and the {@link Change changes}.
 *
 * @author Kirill Likhodedov
 */
public final class GitCommit extends VcsFullCommitDetailsImpl {

  public GitCommit(@NotNull Hash hash, @NotNull List<Hash> parents, long authorTime, @NotNull VirtualFile root, @NotNull String subject,
                   @NotNull String authorName,
                   @NotNull String authorEmail, @NotNull String message, @NotNull String committerName, @NotNull String committerEmail,
                   long commitTime, @NotNull List<Change> changes) {
    super(hash, parents, authorTime, root, subject, authorName, authorEmail, message, committerName, committerEmail, commitTime, changes);
  }
}
