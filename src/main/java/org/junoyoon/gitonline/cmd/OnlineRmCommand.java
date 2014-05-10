package org.junoyoon.gitonline.cmd;

import org.junoyoon.gitonline.model.FileEntry;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuildIterator;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.util.FS;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class OnlineRmCommand extends OnlineCommand {
	@Override
	protected void prepare(List<FileEntry> fileEntries, File rootDir) {
		// no op
	}

	@Override
	protected DirCache createDirCache(Repository repo, File liveFile, File root, Collection<String> paths) {
		DirCache dc = null;
		//noinspection SpellCheckingInspection
		ObjectInserter inserter = repo.newObjectInserter();
		try {
			dc = new DirCache(liveFile, FS.detect());
			dc.lock();
			DirCacheBuilder builder = dc.builder();
			final TreeWalk tw = new TreeWalk(repo);
			tw.reset();
			tw.addTree(new DirCacheBuildIterator(builder));
			tw.setRecursive(true);
			tw.setFilter(PathFilterGroup.createFromStrings(paths));
			//noinspection StatementWithEmptyBody
			while (tw.next()) {
				// no op;
			}
			inserter.flush();
			builder.commit();
			dc.unlock();
		} catch (IOException e) {
			throw new JGitInternalException(
					JGitText.get().exceptionCaughtDuringExecutionOfAddCommand, e);
		} finally {
			inserter.release();
		}
		return dc;
	}
}
