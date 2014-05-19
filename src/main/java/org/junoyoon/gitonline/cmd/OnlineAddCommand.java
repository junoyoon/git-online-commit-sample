package org.junoyoon.gitonline.cmd;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.junoyoon.gitonline.model.FileEntry;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.*;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeIterator;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.eclipse.jgit.util.FS;
import org.junoyoon.gitonline.model.User;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Commit Online file
 */
public class OnlineAddCommand extends OnlineCommand {

	@Override
	public void call(FileRepository repo, List<FileEntry> fileEntries, User author, User committer, String message) {
		List<File> prepare = null;
		try {
			final Git git = new Git(repo);
			FileUtils.deleteQuietly(repo.getIndexFile());
			prepare = prepare(fileEntries, repo.getWorkTree());
			AddCommand command = git.add();
			for (String each : convert(fileEntries)) {
				command.addFilepattern(each);
			}
			command.call();
			final CommitCommand commit = git.commit();
			for (String each : convert(fileEntries)) {
				commit.setOnly(each);
			}
			commit.setMessage(message);
			commit.call();
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			if (prepare != null) {
				for (File each : prepare) {
					FileUtils.deleteQuietly(each);
				}
			}
		}
	}

	protected List<File> prepare(List<FileEntry> fileEntries, File rootDir) {
		List<File> files = new ArrayList<File>();
		try {
			for (FileEntry each : fileEntries) {
				File targetFile = new File(rootDir, each.getPath());
				files.add(targetFile);
				//noinspection ResultOfMethodCallIgnored
				targetFile.getParentFile().mkdirs();
				if (each.getContentBytes() != null) {
					Files.write(each.getContentBytes(), targetFile);
				} else {
					Files.write(each.getContent(), targetFile, Charset.forName("UTF-8"));
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return files;
	}

	public DirCache createDirCache(Repository repo, File liveFile, File root, Collection<String> paths) {
		DirCache dc = null;
		ObjectInserter inserter = repo.newObjectInserter();
		try {
			dc = new DirCache(liveFile, FS.detect());
			dc.lock();
			DirCacheIterator c;
			DirCacheBuilder builder = dc.builder();
			final TreeWalk tw = new TreeWalk(repo);
			tw.addTree(new DirCacheBuildIterator(builder));
			FileTreeIterator workingTreeIterator = new FileTreeIterator(root, FS.detect(), repo.getConfig().get(WorkingTreeOptions.KEY));
			tw.addTree(workingTreeIterator);
			tw.setRecursive(true);
			String lastAddedFile = null;

			while (tw.next()) {
				String path = tw.getPathString();

				WorkingTreeIterator f = tw.getTree(1, WorkingTreeIterator.class);
				if (!(path.equals(lastAddedFile))) {

					c = tw.getTree(0, DirCacheIterator.class);
					if (f != null) { // the file exists
						long sz = f.getEntryLength();
						DirCacheEntry entry = new DirCacheEntry(path);
						if (c == null || c.getDirCacheEntry() == null
								|| !c.getDirCacheEntry().isAssumeValid()) {
							FileMode mode = f.getIndexFileMode(c);
							entry.setFileMode(mode);

							if (FileMode.GITLINK != mode) {
								entry.setLength(sz);
								entry.setLastModified(f
										.getEntryLastModified());
								long contentSize = f
										.getEntryContentLength();
								InputStream in = f.openEntryStream();
								try {
									entry.setObjectId(inserter.insert(
											Constants.OBJ_BLOB, contentSize, in));
								} finally {
									in.close();
								}
							} else
								entry.setObjectId(f.getEntryObjectId());
							builder.add(entry);
							lastAddedFile = path;
						} else {
							builder.add(c.getDirCacheEntry());
						}
					}
				}
			}
			inserter.flush();
			builder.commit();
		} catch (IOException e) {
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfAddCommand, e);
		} finally {
			if (dc != null) {
				dc.unlock();
			}
			inserter.release();
		}
		return dc;
	}


}
