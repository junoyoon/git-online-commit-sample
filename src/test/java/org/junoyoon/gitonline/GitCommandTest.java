package org.junoyoon.gitonline;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.eclipse.jgit.util.StringUtils;
import org.junit.Test;
import org.junoyoon.gitonline.cmd.OnlineAddCommand;
import org.junoyoon.gitonline.cmd.OnlineRmCommand;
import org.junoyoon.gitonline.model.FileEntry;
import org.junoyoon.gitonline.model.User;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;

/**
 * Created by junoyoon on 14. 5. 7.
 */
public class GitCommandTest {

	public Git getGit() {
		try {
			File bareRepoDir = new File(GitCommandTest.class.getResource("/universal-analytics-java.git").getFile());
			File tempDir = new File("c:/project/target/");
			FileUtils.deleteDirectory(tempDir);
			tempDir.mkdirs();
			File tempWorkTree = Files.createTempDir();
			FileUtils.copyDirectory(bareRepoDir, tempDir);
			RepositoryBuilder builder = new RepositoryBuilder().setGitDir(tempDir).setWorkTree(tempWorkTree).readEnvironment();
			FileUtils.deleteQuietly(builder.getIndexFile());
			Repository build = builder.build();
			return new Git(build);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testFileAdd() throws IOException, GitAPIException {
		Git git = getGit();
		addFile(git, "README", "ewewewe");
	}

	private void addFile(Git git, String path, String content) throws GitAPIException {
		String originalRev = null;
		// Add file to bare repo
		User author = new User("admin", "admin@gmail.com");
		User committer = new User("admin2", "admin2@gmail.com");
		FileEntry fileEntry = new FileEntry();
		fileEntry.setPath(path);
		fileEntry.setContent(content);
		OnlineAddCommand onlineAddCommand = new OnlineAddCommand();
		onlineAddCommand.call((FileRepository) git.getRepository(), newArrayList(fileEntry), author, committer, "ADD");
		// See what's changed
		for (RevCommit each : git.log().setMaxCount(1).call()) {
			System.out.println(each.getShortMessage());
		}
	}


	private List<FileEntry> getFileEntries(Git git, String path, String revision, boolean recursive) {
		Repository repository = null;
		List<FileEntry> scripts = newArrayList();
		TreeWalk walk = null;
		RevWalk revwalk = null;
		try {
			repository = git.getRepository();
			CanonicalTreeParser treeParser = new CanonicalTreeParser();
			ObjectId head = repository.resolve(revision);
			walk = new TreeWalk(repository);
			final ObjectReader curs = repository.newObjectReader();
			revwalk = new RevWalk(repository);
			treeParser.reset(curs, revwalk.parseTree(head));

			walk.reset(); // drop the first empty tree, which we do not need here
			if (!StringUtils.equalsIgnoreCase(path, "/") && !StringUtils.isEmptyOrNull(path)) {
				walk.setFilter(PathFilterGroup.createFromStrings(path));
			}

			walk.setRecursive(recursive);
			walk.addTree(treeParser);
			while (walk.next()) {
				final FileMode mode = walk.getFileMode(0);
				FileEntry script = new FileEntry();
				String pathString = walk.getPathString();
				script.setPath(pathString);
				LogCommand log = new Git(repository).log();
				log.addPath(pathString);
				log.setMaxCount(1);

				if (mode == FileMode.EXECUTABLE_FILE || mode == FileMode.REGULAR_FILE) {
					long objectSize = walk.getObjectReader().getObjectSize(walk.getObjectId(0), Constants.OBJ_BLOB);
					script.setFileSize(objectSize);
				} else {
					continue;
				}
				// Make it just head
				scripts.add(script);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			repository.close();
			revwalk.dispose();
		}
		return scripts;
	}

	@Test
	public void testFileList() throws IOException, GitAPIException {
		final Git git = getGit();
		addFile(git, "README2", "ewewewe");
		for (FileEntry each : getFileEntries(git, "", "HEAD", false)) {
			System.out.println(each.getPath());
		}
		addFile(git, "wwww/REAdDME33", "2222222222222222223333333333" + Math.random());
		for (FileEntry each : getFileEntries(git, "", "HEAD", true)) {
			System.out.println(each.getPath());
		}
	}

	@Test
	public void testCommitTest() throws IOException, GitAPIException {
	}

	@Test
	public void testFileDelete() throws IOException, GitAPIException {
		Git git = getGit();
		// Delete file from bare repo
		User author = new User("admin", "admin@gmail.com");
		User committer = new User("admin2", "admin2@gmail.com");
		addFile(git, "wwww/REAdDME33", "2222222222222222223333333333" + Math.random());
		FileEntry fileEntry = new FileEntry();
		fileEntry.setPath("wwww/REAdDME33");
		OnlineRmCommand onlineRmCommand = new OnlineRmCommand();
		onlineRmCommand.call((FileRepository) getGit().getRepository(), newArrayList(fileEntry), author, committer, "DELETE");
		for (RevCommit each : git.log().setMaxCount(1).call()) {
			System.out.println(each.getShortMessage());
		}
	}

}
