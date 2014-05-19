package org.junoyoon.gitonline.cmd;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FS;
import org.junoyoon.gitonline.model.FileEntry;
import org.junoyoon.gitonline.model.User;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junoyoon.gitonline.model.User;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static org.apache.commons.io.FileUtils.deleteQuietly;

public abstract class OnlineCommand {

	protected Collection<String> convert(List<FileEntry> fileEntries) {
		List<String> paths = newArrayList();
		for (FileEntry each : fileEntries) {
			paths.add(each.getPath());
		}
		return paths;
	}

	abstract public void call(FileRepository repo, List<FileEntry> fileEntries, User author, User committer, String message);

}
