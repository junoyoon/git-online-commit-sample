package org.junoyoon.gitonline.cmd;

import com.google.common.io.Files;
import org.junoyoon.gitonline.model.FileEntry;
import org.junoyoon.gitonline.model.User;
import org.eclipse.jgit.api.errors.ConcurrentRefUpdateException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junoyoon.gitonline.model.User;

import java.io.File;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static org.apache.commons.io.FileUtils.deleteQuietly;

public abstract class OnlineCommand {

	public void call(Repository repo, List<FileEntry> fileEntries, User author, User committer, String message) {
		File rootDir = Files.createTempDir();
		File indexDir = Files.createTempDir();
		File liveFile = new File(indexDir, "index");
		try {
			prepare(fileEntries, rootDir);
			DirCache call = createDirCache(repo, liveFile, rootDir, convert(fileEntries));
			commit(repo, call, new PersonIdent(author.getUserId(), author.getEmail()), new PersonIdent(committer.getUserId(), committer.getEmail()), message);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			deleteQuietly(rootDir);
			deleteQuietly(indexDir);
		}
	}

	protected Collection<String> convert(List<FileEntry> fileEntries) {
		List<String> paths = newArrayList();
		for (FileEntry each : fileEntries) {
			paths.add(each.getPath());
		}
		return paths;
	}

	protected abstract void prepare(List<FileEntry> fileEntries, File rootDir);

	protected abstract DirCache createDirCache(Repository repo, File liveFile, File root, Collection<String> paths);


	protected RevCommit commit(Repository repo, DirCache dirCache, PersonIdent committer, PersonIdent author, String message) {
		List<ObjectId> parents = new LinkedList<ObjectId>();
		RevWalk rw = new RevWalk(repo);

		try {
			Ref head = repo.getRef(Constants.HEAD);
			if (head == null)
				throw new NoHeadException(
						JGitText.get().commitOnRepoWithoutHEADCurrentlyNotSupported);

			RepositoryState state = repo.getRepositoryState();
			// determine the current HEAD and the commit it is referring to
			ObjectId headId = repo.resolve(Constants.HEAD + "^{commit}"); //$NON-NLS-1$
			if (headId != null)
				parents.add(0, headId);

			dirCache.lock();
			try {
				ObjectInserter odi = repo.newObjectInserter();
				try {
					// Write the index as tree to the object database. This may
					// fail for example when the index contains unmerged paths
					// (unresolved conflicts)
					ObjectId indexTreeId = dirCache.writeTree(odi);

					// Create a Commit object, populate it and write it
					CommitBuilder commit = new CommitBuilder();
					commit.setCommitter(committer);
					commit.setAuthor(author);
					commit.setMessage(message);

					commit.setParentIds(parents);
					commit.setTreeId(indexTreeId);
					ObjectId commitId = odi.insert(commit);
					odi.flush();

					RevCommit revCommit = rw.parseCommit(commitId);
					RefUpdate ru = repo.updateRef(Constants.HEAD);
					ru.setNewObjectId(commitId);
					String prefix = parents.size() == 0 ? "commit (initial): " //$NON-NLS-1$
							: "commit: "; //$NON-NLS-1$
					ru.setRefLogMessage(
							prefix + revCommit.getShortMessage(), false);
					if (headId != null)
						ru.setExpectedOldObjectId(headId);
					else
						ru.setExpectedOldObjectId(ObjectId.zeroId());
					RefUpdate.Result rc = ru.forceUpdate();
					switch (rc) {
						case NEW:
						case FORCED:
						case FAST_FORWARD: {
							if (state == RepositoryState.MERGING_RESOLVED) {
								// Commit was successful. Now delete the files
								// used for merge commits
								repo.writeMergeCommitMsg(null);
								repo.writeMergeHeads(null);
							} else if (state == RepositoryState.CHERRY_PICKING_RESOLVED) {
								repo.writeMergeCommitMsg(null);
								repo.writeCherryPickHead(null);
							} else if (state == RepositoryState.REVERTING_RESOLVED) {
								repo.writeMergeCommitMsg(null);
								repo.writeRevertHead(null);
							}
							return revCommit;
						}
						case REJECTED:
						case LOCK_FAILURE:
							throw new ConcurrentRefUpdateException(
									JGitText.get().couldNotLockHEAD, ru.getRef(),
									rc);
						default:
							throw new JGitInternalException(MessageFormat.format(
									JGitText.get().updatingRefFailed,
									Constants.HEAD, commitId.toString(), rc));
					}
				} finally {
					odi.release();
				}
			} finally {
				dirCache.unlock();
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			rw.dispose();
		}
	}
}
