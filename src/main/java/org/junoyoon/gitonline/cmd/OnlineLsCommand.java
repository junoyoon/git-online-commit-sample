package org.junoyoon.gitonline.cmd;

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.junoyoon.gitonline.model.FileEntry;

import java.io.IOException;
import java.util.*;

/**
 * Commit Online file
 */
public class OnlineLsCommand {

	private Repository repository;

	public OnlineLsCommand(Repository repository) {
		this.repository = repository;
	}

	public List<FileEntry> getLs(String branch, String path) throws IOException, GitAPIException {
		RevCommit headCommit = getRevCommit(branch);
		if (headCommit == null) {
			return null;
		}

		RevWalk revWalk = new RevWalk(repository);
		RevTree revTree = revWalk.parseTree(headCommit);
		TreeWalk treeWalk = new TreeWalk(repository);
		treeWalk.addTree(revTree);

		if (path.isEmpty()) {
			return filter(path, getFileEntries(path, treeWalk, headCommit));
		}


		PathFilter pathFilter = PathFilter.create(path);
		treeWalk.setFilter(pathFilter);
		while (treeWalk.next()) {
			if (pathFilter.isDone(treeWalk)) {
				break;
			} else if (treeWalk.isSubtree()) {
				treeWalk.enterSubtree();
			}
		}

		if (treeWalk.isSubtree()) {
			treeWalk.enterSubtree();
			return filter(path, getFileEntries(path, treeWalk, headCommit));
		} else {
			try {

				return filter(path, getFileEntry(treeWalk, headCommit));
			} catch (MissingObjectException e) {
				return null;
			}
		}
	}

	public List<FileEntry> filter(String path, Collection<FileEntry> entries) {
		List<FileEntry> fileEntries = Lists.newArrayList();
		for (FileEntry entry : entries) {
			if (StringUtils.isNotEmpty(path)) {
				entry.setPath(path + "/" + entry.getPath());
			}
			entry.setDir(!isFile(entry.getPath(), "HEAD"));
			fileEntries.add(entry);
		}
		return fileEntries;
	}


	/**
	 * {@code treeWalk}가 현재 위치한 파일 메타데이터를 JSON 데이터로 변환하여 반환한다.
	 * 그 파일에 대한 {@code untilCommitId} 혹은 그 이전 커밋 중에서 가장 최근 커밋 정보를 사용하여 Commit
	 * 메시지와 author 정보등을 같이 반환한다.
	 *
	 * @param treeWalk
	 * @param untilCommitId
	 * @return
	 * @throws IOException
	 * @see <a href="https://www.kernel.org/pub/software/scm/git/docs/git-log.html">git log until</a>
	 */
	private Collection<FileEntry> getFileEntry(TreeWalk treeWalk, AnyObjectId untilCommitId) throws IOException, GitAPIException {
		Git git = new Git(repository);

		final RevCommit next = git.log()
				.add(untilCommitId)
				.addPath(treeWalk.getPathString())
				.call()
				.iterator()
				.next();
		long commitTime = next.getCommitTime() * 1000L;
		FileEntry entry = new FileEntry();
		entry.setPath(treeWalk.getPathString());
		//User author = commit.getAuthor();

//		result.put("createdDate", commitTime);
//		result.put("commitMessage", commit.getShortMessage());
//		result.put("commiter", commit.getCommitterName());
//		result.put("commitDate", commitTime);
//		result.put("commitId", untilCommitId.getName());
		ObjectLoader file = repository.open(treeWalk.getObjectId(0));
		entry.setFileSize(file.getSize());
//
//		boolean isBinary = RawText.isBinary(file.openStream());
//		result.put("isBinary", isBinary);

		return Lists.newArrayList(entry);
	}

	/*
	 * 주어진 git 객체 참조 값을 이용해서 commit 객체를 가져온다
     */
	private RevCommit getRevCommit(String revstr) throws IOException {
		ObjectId objectId = getObjectId(revstr);
		return parseCommit(objectId);
	}

	/*
	 * AnyObjectId 를 이용해서 RevCommit 객체를 얻는다
	 */
	private RevCommit parseCommit(AnyObjectId objectId) throws IOException {
		if (objectId == null) {
			return null;
		}
		RevWalk revWalk = new RevWalk(repository);
		return revWalk.parseCommit(objectId);
	}


	/*
	 * 주어진 git 객체 참조 값에 해당하는 것을 가져온다
     */
	private ObjectId getObjectId(String revstr) throws IOException {
		if (revstr == null) {
			return repository.resolve(Constants.HEAD);
		} else {
			return repository.resolve(revstr);
		}
	}


	/**
	 * {@code treeWalk}가 현재 위치한 디렉토리에 들어있는 파일과 디렉토리 메타데이터를 JSON 데이터로 변환하여 반환한다.
	 * 각 파일과 디렉토리에 대한 {@code untilCommitId} 혹은 그 이전 커밋 중에서 가장 최근 커밋 정보를 사용하여 Commit 메시지와 author 정보등을 같이 반홚다.
	 *
	 * @param treeWalk
	 * @param untilCommitId
	 * @return
	 * @throws IOException
	 * @throws GitAPIException
	 * @see <a href="https://www.kernel.org/pub/software/scm/git/docs/git-log.html">git log until</a>
	 */
	private Collection<FileEntry> getFileEntries(String basePath, TreeWalk treeWalk, AnyObjectId untilCommitId) throws IOException, GitAPIException {////
		return new ObjectFinder(basePath, treeWalk, untilCommitId).find();
	}

	public class ObjectFinder {
		private Map<String, FileEntry> found = new HashMap<String, FileEntry>();
		private List<String> targets = new ArrayList<String>();
		private String basePath;
		private AnyObjectId untilCommitId;
		private Iterator<RevCommit> commitIterator;

		public ObjectFinder(String basePath, TreeWalk treeWalk, AnyObjectId untilCommitId) throws IOException, GitAPIException {
			while (treeWalk.next()) {
				String path = treeWalk.getNameString();
				targets.add(path);
			}
			this.basePath = basePath;
			this.untilCommitId = untilCommitId;
			this.commitIterator = getCommitIterator();
		}

		public Collection<FileEntry> find() throws IOException {
			while (shouldFindMore()) {
				RevCommit commit = commitIterator.next();
				Map<String, ObjectId> objects = findObjects(commit);
				found(commit, objects);
			}
			return found.values();
		}

		/*
		 * get commit logs with untilCommitId and basePath
		 */
		private Iterator<RevCommit> getCommitIterator() throws IOException, GitAPIException {
			Git git = new Git(repository);
			LogCommand logCommand = git.log().add(untilCommitId);
			if (StringUtils.isNotEmpty(basePath)) {
				logCommand.addPath(basePath);
			}
			final Iterator<RevCommit> iterator = logCommand.call().iterator();
			return new Iterator<RevCommit>() {
				@Override
				public void remove() {
					iterator.remove();
				}

				@Override
				public RevCommit next() {
					return fixRevCommitNoParents(iterator.next());
				}

				@Override
				public boolean hasNext() {
					return iterator.hasNext();
				}
			};
		}

		private boolean shouldFindMore() {
			// If targets is empty, it means we have found every interested objects and no need to continue.
			if (targets.isEmpty()) {
				return false;
			}
			return commitIterator.hasNext();
		}

		private Map<String, ObjectId> findObjects(RevCommit commit) throws IOException {
			final Map<String, ObjectId> objects = new HashMap<String, ObjectId>();

			// We want to find the latest commit for each of `targets`. We already know they have
			// same `basePath`. So get every blobs and trees match one of `targets`, under the
			// `basePath`, and put them into `objects`.
			TreeWalkHandler objectCollector = new TreeWalkHandler() {
				@Override
				public void handle(TreeWalk treeWalk) {
					if (targets.contains(treeWalk.getNameString())) {
						objects.put(treeWalk.getNameString(), treeWalk.getObjectId(0));
					}
				}
			};

			// Remove every blob and tree from `objects` if any of parent commits have a
			// object whose path and id is identical with the blob or the tree. It means the
			// blob or tree is not changed so we are not interested in it.
			TreeWalkHandler objectRemover = new TreeWalkHandler() {
				@Override
				public void handle(TreeWalk treeWalk) {
					if (treeWalk.getObjectId(0).equals(objects.get(treeWalk.getNameString()))) {
						objects.remove(treeWalk.getNameString());
					}
				}
			};

			// Choose only "interest" objects from the blobs and trees. We are interested in
			// blobs and trees which has change between the last commit and the current commit.
			traverseTree(commit, objectCollector);
			for (RevCommit parent : commit.getParents()) {
				RevCommit fixedParent = fixRevCommitNoTree(parent);
				traverseTree(fixedParent, objectRemover);
			}
			return objects;
		}

		private void traverseTree(RevCommit commit, TreeWalkHandler handler) throws IOException {
			TreeWalk treeWalk;
			if (StringUtils.isEmpty(basePath)) {
				treeWalk = new TreeWalk(repository);
				treeWalk.addTree(commit.getTree());
			} else {
				treeWalk = TreeWalk.forPath(repository, basePath, commit.getTree());
				if (treeWalk == null) {
					return;
				}
				treeWalk.enterSubtree();
			}
			while (treeWalk.next()) {
				handler.handle(treeWalk);
			}
		}

		/*
		 * JGit 의 LogCommand 를 사용하여 commit 조회를 할 때 path 정보를 이용하였을 경우
		 * commit 객체가 부모 commit 에 대한 정보를 가지고 있지 않을 수 있다.
		 * 이러한 현상은 아래의 JGit version 에서 확인 되었다.
		 * 3.1.0.201310021548-r ~ 3.2.0.201312181205-r
		 */
		private RevCommit fixRevCommitNoParents(RevCommit commit) {
			if (commit.getParentCount() == 0) {
				return fixRevCommit(commit);
			}
			return commit;
		}

		/*
		 * fixRevCommitNoParents 를 통해서 가져온 커밋의 parents 는 tree 정보가 없을 수 있다
		 */
		private RevCommit fixRevCommitNoTree(RevCommit commit) {
			if (commit.getTree() == null) {
				return fixRevCommit(commit);
			}
			return commit;
		}

		private RevCommit fixRevCommit(RevCommit commit) {
			RevWalk revWalk = new RevWalk(repository);
			try {
				return revWalk.parseCommit(commit);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		/*
		 * Now, every objects in `objects` are interested. Get metadata from the objects, put
		 * them into `found` and remove from `targets`.
		 */
		private void found(RevCommit revCommit, Map<String, ObjectId> objects) {
			for (String path : objects.keySet()) {
				FileEntry fileEntry = new FileEntry();
				fileEntry.setPath(path);

				//revCommit.getShortMessage()
				// revCommit.getCommitterIdent().getName()

//				data.put("createdDate", revCommit.getCommitTime() * 1000l);
//				data.put("author", revCommit.getAuthorIdent().getName());

				found.put(path, fileEntry);
				targets.remove(path);
			}
		}
	}

	public boolean isFile(String path, String revStr) {
		ObjectId objectId = null;
		try {
			objectId = getObjectId(revStr);
			if (objectId == null) {
				return false;
			}

			RevWalk revWalk = new RevWalk(repository);
			RevTree revTree = revWalk.parseTree(objectId);
			TreeWalk treeWalk = new TreeWalk(repository);
			treeWalk.addTree(revTree);

			while (treeWalk.next()) {
				if (treeWalk.getPathString().equals(path) && !treeWalk.isSubtree()) {
					return true;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public static interface TreeWalkHandler {
		void handle(TreeWalk treeWalk);
	}
}
