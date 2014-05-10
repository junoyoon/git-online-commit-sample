package org.junoyoon.gitonline;

import com.google.common.collect.Lists;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;
import org.junoyoon.gitonline.cmd.OnlineAddCommand;
import org.junoyoon.gitonline.cmd.OnlineRmCommand;
import org.junoyoon.gitonline.model.FileEntry;
import org.junoyoon.gitonline.model.User;

import java.io.File;
import java.io.IOException;

/**
 * Created by junoyoon on 14. 5. 7.
 */
public class GitCommandTest {

	public Git getGit() {
		try {
			File bareRepoDir = new File(GitCommandTest.class.getResource("/universal-analytics-java.git").getFile());
			File tempDir = Files.createTempDir();
			tempDir.deleteOnExit();
			FileUtils.copyDirectory(bareRepoDir, tempDir);
			RepositoryBuilder builder = new RepositoryBuilder().setBare().setGitDir(bareRepoDir).readEnvironment();
			Repository build = builder.build();
			return new Git(build);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testFileAdd() throws IOException, GitAPIException {
		Git git = getGit();
		String originalRev = null;
		// Add file to bare repo
		User author = new User("admin", "admin@gmail.com");
		User committer = new User("admin2", "admin2@gmail.com");
		FileEntry fileEntry = new FileEntry();
		fileEntry.setPath("README.d");
		fileEntry.setContent("1111112\n2332");
		OnlineAddCommand onlineAddCommand = new OnlineAddCommand();
		onlineAddCommand.call(git.getRepository(), Lists.newArrayList(fileEntry), author, committer, "ADD");
		// See what's changed
		for (RevCommit each : git.log().setMaxCount(1).call()) {
			System.out.println(each.getShortMessage());
		}
	}

	@Test
	public void testFileDelete() throws IOException, GitAPIException {
		Git git = getGit();
		// Delete file from bare repo
		User author = new User("admin", "admin@gmail.com");
		User committer = new User("admin2", "admin2@gmail.com");
		FileEntry fileEntry = new FileEntry();
		fileEntry.setPath("www/www");
		OnlineRmCommand onlineRmCommand = new OnlineRmCommand();
		onlineRmCommand.call(getGit().getRepository(), Lists.newArrayList(fileEntry), author, committer, "DELETE");
		for (RevCommit each : git.log().setMaxCount(1).call()) {
			System.out.println(each.getShortMessage());
		}
	}

}
