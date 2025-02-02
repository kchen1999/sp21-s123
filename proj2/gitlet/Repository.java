package gitlet;

import java.io.File;
import java.util.*;

import static gitlet.Utils.*;

/** Represents a gitlet repository.

 The main functionality that gitlet supports is:
    1. Saving the contents of entire directories of files - called committing and the saved content
    themselves are called commits
    2. Restoring a version of one or more files or entire commits - called checking out those files or
    that commit
    3. Viewing the history of your backups - history can be viewed in something called the log
    4. Maintaining related sequences of commits called branches
    5. Merging changes made in one branch into another

 *  @author Kevin
 */
public class Repository {
    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /** The commit tree - mapping of commit references to their commit. */
    private static HashMap<String, Commit> commits = new HashMap<>();
    /** The abbreviated commit tree - mapping of 6 digit commit abbreviations to their commit reference*/
    private static HashMap<String, ArrayList<String>> commitPrefixes= new HashMap<>();
    /** Mapping of branch names to references to commits */
    private static TreeMap<String, Stack<String>> branches = new TreeMap<>();
    /** Staging area (files staged for addition): Mapping of file names to blob references */
    private static TreeMap<String, String> additions = new TreeMap<>();
    /** Staging area (files staged for removal): Mapping of file names to blob references */
    private static TreeMap<String, String> removals = new TreeMap<>();
    /** Mapping of blob references to file contents **/
    private static HashMap<String, File> blobMap = new HashMap<>();
    /** The current branch */
    private static String currentBranch;

    private static HashMap<String, Commit> commitsFromFile() {
        File inFile = join(GITLET_DIR, "commits");
        return readObject(inFile, HashMap.class);
    }

    private static HashMap<String, ArrayList<String>> commitPrefixesFromFile() {
        File inFile = join(GITLET_DIR, "commitPrefixes");
        return readObject(inFile, HashMap.class);
    }

    private static TreeMap<String, Stack<String>> branchesFromFile() {
        File inFile = join(GITLET_DIR, "branches");
        return readObject(inFile, TreeMap.class);
    }

    private static String currentBranchFromFile() {
        File inFile = join(GITLET_DIR, "currentBranch");
        return readObject(inFile, String.class);
    }

    private static TreeMap<String, String> additionsFromFile() {
        File additionsFile = join(GITLET_DIR, "stagingArea", "additions");
        return readObject(additionsFile, TreeMap.class);
    }

    private static TreeMap<String, String> removalsFromFile() {
        File removalsFile = join(GITLET_DIR, "stagingArea", "removals");
        return readObject(removalsFile, TreeMap.class);
    }

    private static HashMap<String, File> blobMapFromFile() {
        File inFile = join(GITLET_DIR, "blobMap");
        return readObject(inFile, HashMap.class);
    }

    private static void checkGitletDirIsInitialized() {
        if (!GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
    }

    private static String getHeadCommitId() {
        branches = branchesFromFile();
        currentBranch = currentBranchFromFile();
        return branches.get(currentBranch).peek();
    }

    private static Commit getHeadCommit() {
        return getCommit(getHeadCommitId());
    }

    private static Commit getCommit(String commitUID) {
        commits = commitsFromFile();
        return commits.get(commitUID);
    }

    private static void addCommitPrefix(String commitUID) {
        File commitPrefixesFile = join(GITLET_DIR, "commitPrefixes");
        commitPrefixes = commitPrefixesFromFile();
        String commitUIDAbbreviated = commitUID.substring(0, 6);
        String commitUIDRest = commitUID.substring(6, commitUID.length());
        ArrayList<String> commitUIDRemainder = commitPrefixes.get(commitUIDAbbreviated);
        if (commitUIDRemainder == null) {
            commitUIDRemainder = new ArrayList<>();
        }
        commitUIDRemainder.add(commitUIDRest);
        commitPrefixes.put(commitUIDAbbreviated, commitUIDRemainder);
        writeObject(commitPrefixesFile, commitPrefixes);
    }

    private static void clearStagingArea() {
        File additionsFile = join(GITLET_DIR, "stagingArea", "additions");
        File removalsFile = join(GITLET_DIR, "stagingArea", "removals");
        additions = additionsFromFile();
        removals = removalsFromFile();
        additions.clear();
        removals.clear();
        writeObject(additionsFile, additions);
        writeObject(removalsFile, removals);
    }

    private static boolean isCurrentlyTrackedByCurrentCommit(String headCommitBlobUID) {
        return headCommitBlobUID != null;
    }

    private static boolean isIdenticalFile(String f1, String f2) {
        return f1.equals(f2);
    }

    private static boolean isIdenticalToCurrentCommitVersion(String headCommitBlobUID, String blobUID) {
        return isCurrentlyTrackedByCurrentCommit(headCommitBlobUID) && isIdenticalFile(headCommitBlobUID, blobUID);
    }

    private static void removeFileStagedForAddition(String fileName) {
        File additionsFile = join(GITLET_DIR, "stagingArea", "additions");
        additions.remove(fileName);
        writeObject(additionsFile, additions);
    }

    private static void removeFileStagedForRemoval(String fileName) {
        File removalsFile = join(GITLET_DIR, "stagingArea", "removals");
        removals.remove(fileName);
        writeObject(removalsFile, removals);
    }

    private static void stageFileForAddition(String fileName, String blobUID) {
        File additionsFile = join(GITLET_DIR, "stagingArea", "additions");
        additions.put(fileName, blobUID);
        writeObject(additionsFile, additions);
    }

    private static void stageFileForRemoval(String fileName, String blobUID) {
        File removalsFile = join(GITLET_DIR, "stagingArea", "removals");
        removals.put(fileName, blobUID);
        writeObject(removalsFile, removals);
    }

    private static String getCurrentVersionBlobUID(String fileName) {
        Commit headCommit = getHeadCommit();
        HashMap<String, String> headCommitTrackedFiles = headCommit.getTrackedFiles();
        return headCommitTrackedFiles.get(fileName);
    }

    private static void checkIfFileExistsInCommit(HashMap<String, String> trackedFiles, String fileName) {
        if (trackedFiles == null || !trackedFiles.containsKey(fileName)) {
            System.out.println("File does not exist in that commit.");
            System.exit(0);
        }
    }

    private static HashMap<String, String> getBranchCommitFiles(String branchName) {
        branches = branchesFromFile();
        commits = commitsFromFile();
        String commitReference = branches.get(branchName).peek();
        return commits.get(commitReference).getTrackedFiles();
    }

    private static void printCommitIDErrorMessage() {
        System.out.println("No commit with that id exists.");
        System.exit(0);
    }


    private static String getFullCommitUID (String commitUID) {
        if (commitUID.length() < 6) {
            printCommitIDErrorMessage();
        }
        commitPrefixes = commitPrefixesFromFile();
        ArrayList<String> commitUIDRemainder = commitPrefixes.get(commitUID.substring(0, 6));
        if (commitUIDRemainder == null) {
            printCommitIDErrorMessage();
        }
        if (commitUID.length() == 6) {
            return commitUID.concat(commitUIDRemainder.get(0));
        }
        for (String s : commitUIDRemainder) {
            if (commitUID.substring(6, commitUID.length()).equals(s.substring(0, commitUID.length() - 6))) {
                return commitUID.substring(0, 6).concat(s);
            }
        }
        printCommitIDErrorMessage();
        return null;
    }

    private static boolean isStagedForAddition(String fileName, TreeMap<String, String> additions) {
        return additions.containsKey(fileName);
    }

    private static boolean isStagedForRemoval(String fileName, TreeMap<String, String> removals) {
        return removals.containsKey(fileName);
    }

    private static boolean noFilesStagedForAddition() {
        additions = additionsFromFile();
        return additions.isEmpty();
    }

    private static boolean noFilesStagedForRemoval() {
        removals = removalsFromFile();
        return removals.isEmpty();
    }

    private static HashMap<String, String> getCurrentCommitTrackedFiles() {
        return getHeadCommit().getTrackedFiles();
    }

    private static boolean isTrackedByCurrentCommit(String fileName) {
        return getCurrentCommitTrackedFiles().containsKey(fileName);
    }

    /*
        Set up .gitlet directory, staging area directory and blobs directory.
        Set up commit prefixes file, blob maps file, additions file, and removals file.
     */
    public static void setUpPersistence () {
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system already exists in the current directory.");
            System.exit(0);
        }
        GITLET_DIR.mkdir();
        File stagingAreaDir = join(GITLET_DIR, "stagingArea");
        stagingAreaDir.mkdir();
        File blobs = join(GITLET_DIR, "blobs");
        blobs.mkdir();

        File commitPrefixesFile = join(GITLET_DIR, "commitPrefixes");
        File blobMapFile = join(GITLET_DIR, "blobMap");
        File additionsFile = join(GITLET_DIR, "stagingArea", "additions");
        File removalsFile = join(GITLET_DIR, "stagingArea", "removals");
        writeObject(commitPrefixesFile, commitPrefixes);
        writeObject(blobMapFile, blobMap);
        writeObject(additionsFile, additions);
        writeObject(removalsFile, removals);
    }

    /*
        Creates a new Gitlet version-control system in the current directory.
        The system will automatically start with one commit; a commit that contains no files
        and has the commit message initial commit.
        It will have one branch: master and master will be the current branch.
     */
    public static void init() {
        setUpPersistence();
        File commitsFile = join(GITLET_DIR, "commits");
        File branchesFile = join(GITLET_DIR, "branches");
        File currentBranchFile = join(GITLET_DIR, "currentBranch");

        Commit initialCommit = new Commit("initial commit", null);
        Stack<String> parentPointers = new Stack();
        parentPointers.push(initialCommit.getCommitUID());
        commits.put(initialCommit.getCommitUID(), initialCommit);
        branches.put("master", parentPointers);
        currentBranch = "master";

        writeObject(commitsFile, commits);
        writeObject(branchesFile, branches);
        writeObject(currentBranchFile, currentBranch);
    }

    /*
        Saves a snapshot of tracked files in the current commit and staging area so they can be restored at a
        later time, creating a new commit.

        By default, each commit’s snapshot of files will be exactly the same as its parent commit’s snapshot of files;
        it will keep versions of files exactly as they are, and not update them.

        A commit will only update the contents of files it is tracking that have been staged for addition at the time
        of commit, in which case the commit will now include the version of the file that was staged instead of the
        version it got from its parent.

        A commit will save and start tracking any files that were staged for addition but weren’t tracked by its parent.

        Finally, files tracked in the current commit may be untracked in the new commit as a result being staged for
        removal by the rm command
     */

    public static void commit(String message) {
        checkGitletDirIsInitialized();
        File commitsFile = join(GITLET_DIR, "commits");
        File branchesFile = join(GITLET_DIR, "branches");
        commits = commitsFromFile();
        currentBranch = currentBranchFromFile();

        Commit parentCommit = getHeadCommit();
        Commit newCommit = new Commit(message, parentCommit.getCommitUID());
        if (message.contains("Merged ") && message.contains("into " + currentBranch)) {
            newCommit.updateFileContents(parentCommit.getTrackedFiles(), true);
        } else {
            newCommit.updateFileContents(parentCommit.getTrackedFiles(), false);
        }
        commits.put(newCommit.getCommitUID(), newCommit);
        Stack<String> parentPointers = branches.get(currentBranch);
        if (!parentCommit.isSplitPoint()) {
            parentPointers.pop();
        }
        parentPointers.push(newCommit.getCommitUID());
        branches.put(currentBranch, parentPointers);

        addCommitPrefix(newCommit.getCommitUID());
        writeObject(commitsFile, commits);
        writeObject(branchesFile, branches);
        clearStagingArea();
    }

    /*
        Adds a copy of the file as it currently exists to the staging area.
        Staging an already staged file overwrites the previous entry in the staging area
        with the new contents.
        If the current working version of the file is identical to the version in the current
        commit, do not stage it to be added, and remove it from the staging area if it is
        already there
     */

    public static void add(String fileName) {
        checkGitletDirIsInitialized();
        File file = new File(fileName);
        File blobMapFile = join(GITLET_DIR, "blobMap");
        if (!file.exists()) {
            System.out.println("File does not exist");
            System.exit(0);
        }
        additions = additionsFromFile();
        removals = removalsFromFile();
        blobMap = blobMapFromFile();

        String currentVersionBlobUID = getCurrentVersionBlobUID(fileName);
        String blobUID = sha1(readContents(file));

        if (isIdenticalToCurrentCommitVersion(currentVersionBlobUID, blobUID)) {
            if (isStagedForAddition(fileName, additions)) {
                removeFileStagedForAddition(fileName);
            }
            if (isStagedForRemoval(fileName, removals)) {
                removeFileStagedForRemoval(fileName);
            }
            return;
        }
        stageFileForAddition(fileName, blobUID);
        File fileCopy = join(GITLET_DIR, "blobs", blobUID);
        writeContents(fileCopy, readContents(file));
        blobMap.put(blobUID, fileCopy);
        writeObject(blobMapFile, blobMap);
    }

    /*
        Unstage the file if it is currently staged for addition.
        If the file is tracked in the current commit, stage it for removal and remove the file from the
        working directory if the user has not already done so.
     */

    public static void rm(String fileName) {
        checkGitletDirIsInitialized();
        additions = additionsFromFile();
        removals = removalsFromFile();
        String currentVersionBlobUID = getCurrentVersionBlobUID(fileName);
        if (isStagedForAddition(fileName, additions)) {
            removeFileStagedForAddition(fileName);
        } else if (currentVersionBlobUID != null) {
            File f = new File(fileName);
            if (f.exists()) {
                f.delete();
            }
            stageFileForRemoval(fileName, currentVersionBlobUID);
        } else {
            System.out.println("No reasons to remove the file.");
            System.exit(0);
        }
    }

    private static void printIndividualCommit(String commitHash, Commit commit) {
        System.out.println("===");
        System.out.println("commit " + commitHash);
        if (commit.getParent1UID() != null) {
            System.out.println("Merge: " + commit.getParentUID().substring(0, 7)
                    + commit.getParent1UID().substring(0, 7));
        }
        String date = String.format("%1$ta %1$tb %1$te %1$tH:%1$tM:%1$tS %1$tY %1$tz", commit.getTimestamp());
        System.out.println("Date: " + date);
        System.out.println(commit.getMessage());
        System.out.println("");
    }

    /*
        Starting at the current head commit, display information about each commit backwards along the commit tree
        until the initial commit, following the first parent commit links, ignoring any second parents found in merge commits.
     */

    public static void log() {
        checkGitletDirIsInitialized();
        commits = commitsFromFile();
        Commit headCommit = getHeadCommit();
        while (headCommit != null) {
            printIndividualCommit(headCommit.getCommitUID(), headCommit);
            String parentCommitUID = headCommit.getParentUID();
            headCommit = commits.get(parentCommitUID);
        }
    }

    /*
        Like log, except displays information about all commits ever made. The order of the commits does not matter.
     */

    public static void globalLog() {
        checkGitletDirIsInitialized();
        commits = commitsFromFile();
        for (String commitUID : commits.keySet()) {
            Commit commit = commits.get(commitUID);
            printIndividualCommit(commitUID, commit);
        }
    }

    /*
        Prints out the ids of all commits that have the given commit message, one per line.
        If there are multiple such commits, it prints the ids out on separate lines.
     */

    public static void find(String message) {
        checkGitletDirIsInitialized();
        commits = commitsFromFile();
        Boolean found = false;
        for (String commitUID : commits.keySet()) {
            Commit commit = commits.get(commitUID);
            if (message.equals(commit.getMessage())) {
                System.out.println(commitUID);
                found = true;
            }
        }
        if (!found) {
            System.out.println("Found no commit with that message.");
        }
    }

    private static TreeMap<String, String> getModificationsNotStagedForCommit () {
        TreeMap<String, String> map = new TreeMap<>();
        additions = additionsFromFile();
        removals = removalsFromFile();
        List<String> wkDirFiles = plainFilenamesIn(CWD);
        for (String fileName : wkDirFiles) {
            File f = new File(fileName);
            String blobUID = sha1(readContents(f));
            if (isTrackedByCurrentCommit(fileName) && !isIdenticalFile(getCurrentVersionBlobUID(fileName), blobUID)
                    && !isStagedForAddition(fileName, additions)) {
                map.put(fileName, "modified");
            } else if (isStagedForAddition(fileName, additions) &&
                    !isIdenticalFile(additions.get(fileName), blobUID)) {
                map.put(fileName, "modified");
            }
        }
        for (String fileName : additions.keySet()) {
            if (!wkDirFiles.contains(fileName)) {
                map.put(fileName, "deleted");
            }
        }
        for (String fileName : getCurrentCommitTrackedFiles().keySet()) {
            if (!wkDirFiles.contains(fileName) && !isStagedForRemoval(fileName, removals)) {
                map.put(fileName, "deleted");
            }
        }
        return map;
    }

    private static ArrayList<String> getUntrackedFiles() {
        ArrayList<String> list = new ArrayList<>();
        List<String> wkDirFiles = plainFilenamesIn(CWD);
        additions = additionsFromFile();
        removals = removalsFromFile();
        for (String fileName : wkDirFiles) {
            if (!isTrackedByCurrentCommit(fileName) && !isStagedForAddition(fileName, additions)) {
                list.add(fileName);
            }
        }
        return list;
    }

    /*
        Displays what branches currently exist, and marks the current branch with a *.
        Also displays what files have been staged for addition or removal.
        A file in the working directory is “modified but not staged” if it is

        - Tracked in the current commit, changed in the working directory, but not staged; or
        - Staged for addition, but with different contents than in the working directory; or
        - Staged for addition, but deleted in the working directory; or
        - Not staged for removal, but tracked in the current commit and deleted from the working directory.

        The final category (“Untracked Files”) is for files present in the working directory but neither staged
        for addition nor tracked. This includes files that have been staged for removal, but then re-created without
        Gitlet’s knowledge.
     */

    public static void status() {
        checkGitletDirIsInitialized();
        branches = branchesFromFile();
        additions = additionsFromFile();
        removals = removalsFromFile();
        currentBranch = currentBranchFromFile();
        TreeMap<String, String> unstagedModifications = getModificationsNotStagedForCommit();
        ArrayList<String> untrackedFiles = getUntrackedFiles();
        System.out.println("=== Branches ===");
        for (String branch : branches.keySet()) {
            if (branch.equals(currentBranch)) {
                System.out.println("*" + branch);
            } else {
                System.out.println(branch);
            }
        }
        System.out.println("");
        System.out.println("=== Staged Files ===");
        for (String addition : additions.keySet()) {
            System.out.println(addition);
        }
        System.out.println("");
        System.out.println("=== Removed Files ===");
        for (String removal : removals.keySet()) {
            System.out.println(removal);
        }
        System.out.println("");
        System.out.println("=== Modifications Not Staged For Commit ===");
        for (String fileName : unstagedModifications.keySet()) {
            System.out.println(fileName + " (" + unstagedModifications.get(fileName) + ")");
        }
        System.out.println("");
        System.out.println("=== Untracked Files ===");
        for (String fileName : untrackedFiles) {
            System.out.println(fileName);
        }
        System.out.println("");
    }

    private static void overwriteCurrentFileVersion(HashMap<String, String> trackedFiles, String fileName) {
        File currentFile = new File(fileName);
        blobMap = blobMapFromFile();
        checkIfFileExistsInCommit(trackedFiles, fileName);
        String commitVersionBlobUID = trackedFiles.get(fileName);
        File commitVersion = blobMap.get(commitVersionBlobUID);
        writeContents(currentFile, readContents(commitVersion));
    }

    private static void overwriteCurrentWorkingDirectory(HashMap<String, String> branchCommitTrackedFiles) {
        for (String fileName : branchCommitTrackedFiles.keySet()) {
            File currentFile = new File(fileName);
            blobMap = blobMapFromFile();
            String commitVersionBlobUID = branchCommitTrackedFiles.get(fileName);
            File commitVersion = blobMap.get(commitVersionBlobUID);
            writeContents(currentFile, readContents(commitVersion));
        }
    }

    private static void removeTrackedFilesNotInCommit(HashMap<String, String> currentCommitTrackedFiles,
                                                      HashMap<String, String> givenCommitTrackedFiles) {
        for (String fileName : currentCommitTrackedFiles.keySet()) {
            if (!givenCommitTrackedFiles.containsKey(fileName)) {
                File f = new File(fileName);
                f.delete();
            }
        }
    }

    /*
        Takes the version of the file as it exists in the head commit and puts it in the working directory,
        overwriting the version of the file that’s already there if there is one.
        The new version of the file is not staged.
     */

    public static void checkout(String fileName) {
        checkGitletDirIsInitialized();
        Commit headCommit = getHeadCommit();
        overwriteCurrentFileVersion(headCommit.getTrackedFiles(), fileName);
    }

    /*
        Takes the version of the file as it exists in the commit with the given id, and puts it in the working directory,
        overwriting the version of the file that’s already there if there is one.
        The new version of the file is not staged.
     */

    public static void checkout(String commitUID, String fileName) {
        checkGitletDirIsInitialized();
        if (commitUID.length() < 40) {
            commitUID = getFullCommitUID(commitUID);
        }
        Commit commit = getCommit(commitUID);
        if (commit == null) {
            printCommitIDErrorMessage();
        }
        overwriteCurrentFileVersion(commit.getTrackedFiles(), fileName);
    }

    /*
        Takes all files in the commit at the head of the given branch, and puts them in the working directory, overwriting the
        versions of the files that are already there if they exist.

        Also, at the end of this command, the given branch will now be considered the current branch (HEAD).
        Any files that are tracked in the current branch but are not present in the checked-out branch are deleted.

        The staging area is cleared, unless the checked-out branch is the current branch.
     */

    public static void checkoutBranch(String branchName) {
        checkGitletDirIsInitialized();
        branches = branchesFromFile();
        File currentBranchFile = join(GITLET_DIR, "currentBranch");
        currentBranch = currentBranchFromFile();
        ArrayList<String> untrackedFiles = getUntrackedFiles();
        if (!branches.containsKey(branchName)) {
            System.out.println("No such branch exists.");
            System.exit(0);
        } else if (branchName.equals(currentBranch)) {
            System.out.println("No need to checkout the current branch.");
            System.exit(0);
        } else if (untrackedFiles.size() != 0) {
            System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
            System.exit(0);
        }
        HashMap<String, String> branchCommitTrackedFiles = getBranchCommitFiles(branchName);
        overwriteCurrentWorkingDirectory(branchCommitTrackedFiles);
        removeTrackedFilesNotInCommit(getCurrentCommitTrackedFiles(), branchCommitTrackedFiles);
        currentBranch = branchName;
        writeObject(currentBranchFile, currentBranch);
        clearStagingArea();
    }

    /*
        Creates a new branch with the given name, and points it at the current head commit.
        This command does not immediately switch to the newly created branch.
     */

    public static void branch(String branchName) {
        checkGitletDirIsInitialized();
        File branchesFile = join(GITLET_DIR, "branches");
        File commitsFile = join(GITLET_DIR, "commits");
        branches = branchesFromFile();
        currentBranch = currentBranchFromFile();
        commits = commitsFromFile();
        if (branches.containsKey(branchName)) {
            System.out.println("A branch with that name already exists.");
            System.exit(0);
        }
        Commit currentCommit = getHeadCommit();
        currentCommit.setSplitPoint();
        commits.put(getHeadCommitId(), currentCommit);
        Stack<String> newBranchParentPointers = new Stack<>();
        Stack<String> currentBranchParentPointers = branches.get(currentBranch);
        for (String commitUID : currentBranchParentPointers) {
            newBranchParentPointers.push(commitUID);
        }
        branches.put(branchName, newBranchParentPointers);
        writeObject(branchesFile, branches);
        writeObject(commitsFile, commits);
    }

    /*
        Deletes the branch with the given name.
     */
    public static void rmBranch(String branchName) {
        checkGitletDirIsInitialized();
        branches = branchesFromFile();
        currentBranch = currentBranchFromFile();
        File branchesFile = join(GITLET_DIR, "branches");
        if (!branches.containsKey(branchName)) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        } else if (branchName.equals(currentBranch)) {
            System.out.println("Cannot remove the current branch.");
            System.exit(0);
        }
        branches.remove(branchName);
        writeObject(branchesFile, branches);
    }

    /*
        Checks out all the files tracked by the given commit.
        Removes tracked files that are not present in that commit.
        Also moves the current branch’s head to that commit node.
     */

    public static void reset(String commitUID) {
        checkGitletDirIsInitialized();
        if (commitUID.length() < 40) {
            commitUID = getFullCommitUID(commitUID);
        }
        Commit commit = getCommit(commitUID);
        if (commit == null) {
            printCommitIDErrorMessage();
        }
        File branchesFile = join(GITLET_DIR, "branches");
        branches = branchesFromFile();
        currentBranch = currentBranchFromFile();
        ArrayList<String> untrackedFiles = getUntrackedFiles();
        if (untrackedFiles.size() != 0) {
            System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
            System.exit(0);
        }
        HashMap<String, String> givenCommitTrackedFiles = commit.getTrackedFiles();
        overwriteCurrentWorkingDirectory(givenCommitTrackedFiles);
        removeTrackedFilesNotInCommit(getCurrentCommitTrackedFiles(), givenCommitTrackedFiles);
        Stack<String> parentPointers = branches.get(currentBranch);
        parentPointers.pop();
        parentPointers.push(commitUID);
        branches.put(currentBranch, parentPointers);
        writeObject(branchesFile, branches);
        clearStagingArea();
    }

    /*
        The split point is the latest common ancestor of the current and given branch heads.

        If the split point is the same commit as the given branch, then we do nothing; the merge is complete, and the
        operation ends with the message Given branch is an ancestor of the current branch.

        If the split point is the current branch, then the effect is to check out the given branch, and the operation
        ends after printing the message Current branch fast-forwarded.
     */

    private static Commit findSplitPoint(String currentBranch, String givenBranch) {
        branches = branchesFromFile();
        commits = commitsFromFile();
        Stack<String> currentBranchParents = branches.get(currentBranch);
        Stack<String> givenBranchParents = branches.get(givenBranch);
        String splitPointUID = null;
        for (String s1 : currentBranchParents) {
            if (splitPointUID != null) {
                break;
            }
            for (String s2: givenBranchParents) {
                if (s1.equals(s2)) {
                    splitPointUID = s1;
                    break;
                }
            }
        }
        String currentBranchCommitUID= branches.get(currentBranch).peek();
        String givenBranchCommitUID= branches.get(givenBranch).peek();
        if(splitPointUID.equals(givenBranchCommitUID)) {
            System.out.println("Given branch is an ancestor of the current branch.");
            System.exit(0);
        }
        if (splitPointUID.equals(currentBranchCommitUID)) {
            checkoutBranch(givenBranch);
            System.out.println("Current branch fast-forwarded.");
            System.exit(0);
        }
        return getCommit(splitPointUID);
    }

    private static void mergeFileContents(String fileName, String currentBranchFileUID, String givenBranchFileUID) {
        File file = join(CWD, fileName);
        file.delete();
        blobMap = blobMapFromFile();
        if (currentBranchFileUID == null) {
            writeContents(file, "<<<<<<< HEAD\n", "=======\n",
                    readContents(blobMap.get(givenBranchFileUID)), ">>>>>>>\n");
        } else if (givenBranchFileUID == null) {
            writeContents(file, "<<<<<<< HEAD\n", readContents(blobMap.get(currentBranchFileUID)),
                    "=======\n", ">>>>>>>\n");
        } else {
            writeContents(file, "<<<<<<< HEAD\n", readContents(blobMap.get(currentBranchFileUID)),
                    "=======\n", readContents(blobMap.get(givenBranchFileUID)), ">>>>>>>\n");
        }
        String blobUID = sha1(readContents(file));
        stageFileForAddition(fileName, blobUID);
    }

    /*
        Merges files from the given branch into the current branch.

        1. Any files that have been modified in the given branch since the split point, but not modified in the
           current branch since the split point should be changed to their versions in the given branch
           (checked out from the commit at the front of the given branch).
           These files should then all be automatically staged.

        2. Any files that have been modified in the current branch but not in the given branch since the split point
           should stay as they are.

        3. Any files that have been modified in both the current and given branch in the same way
           (i.e., both files now have the same content or were both removed) are left unchanged by the merge.

        4. Any files that were not present at the split point and are present only in the current branch should
           remain as they are.

        5. Any files that were not present at the split point and are present only in the given branch should
           be checked out and staged.

        6. Any files present at the split point, unmodified in the current branch, and absent in the given branch
           should be removed (and untracked).

        7. Any files present at the split point, unmodified in the given branch, and absent in the current branch
           should remain absent.

        8. Any files modified in different ways in the current and given branches are in conflict.
          “Modified in different ways” can mean that the contents of both are changed and different from other, or
          the contents of one are changed and the other file is deleted, or the file was absent at the split point
          and has different contents in the given and current branches.

          In this case, replace the contents of the conflicted file with:
          <<<<<<< HEAD
          contents of file in current branch
          =======
          contents of file in given branch
          >>>>>>>

        Once files have been updated according to the above, and the split point was not the current branch or the
        given branch, merge automatically commits with the log message
        Merged [given branch name] into [current branch name].

        Then, if the merge encountered a conflict, print the message Encountered a merge conflict. on the terminal.
     */

    public static void merge(String branchName) {
        checkGitletDirIsInitialized();
        Boolean mergeConflict = false;
        branches = branchesFromFile();
        currentBranch = currentBranchFromFile();
        ArrayList<String> untrackedFiles = getUntrackedFiles();

        if (!noFilesStagedForAddition() || !noFilesStagedForRemoval()) {
            System.out.println("You have uncommitted changes.");
            System.exit(0);
        } else if (!branches.containsKey(branchName)) {
            System.out.println("A branch with that name does not exist.");
            System.exit(0);
        } else if (branchName.equals(currentBranch)) {
            System.out.println("Cannot merge a branch with itself.");
            System.exit(0);
        } else if (untrackedFiles.size() != 0) {
            System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
            System.exit(0);
        }

        Commit splitPoint = findSplitPoint(currentBranch, branchName);
        HashMap<String, String> splitPointCommitFiles = splitPoint.getTrackedFiles();
        HashMap<String, String> currentBranchCommitFiles = getCurrentCommitTrackedFiles();
        HashMap<String, String> givenBranchCommitFiles = getBranchCommitFiles(branchName);
        for (String fileName : splitPointCommitFiles.keySet()) {
            String splitPointFileUID = splitPointCommitFiles.get(fileName);
            String currentBranchFileUID = currentBranchCommitFiles.get(fileName);
            String givenBranchFileUID = givenBranchCommitFiles.get(fileName);
            if (currentBranchFileUID == null && givenBranchFileUID == null) {
                continue;
            } else if (currentBranchFileUID == null && isIdenticalFile(splitPointFileUID, givenBranchFileUID)) {
                continue;
            } else if (currentBranchFileUID == null && !isIdenticalFile(splitPointFileUID, givenBranchFileUID)) {
                mergeFileContents(fileName, null, givenBranchFileUID);
                mergeConflict = true;
            } else if (givenBranchFileUID == null && isIdenticalFile(splitPointFileUID, currentBranchFileUID)) {
                rm(fileName);
            } else if (givenBranchFileUID == null && !isIdenticalFile(splitPointFileUID, currentBranchFileUID)) {
                mergeFileContents(fileName, currentBranchFileUID, null);
                mergeConflict = true;
            } else if (!isIdenticalFile(splitPointFileUID, currentBranchFileUID) && isIdenticalFile(currentBranchFileUID, givenBranchFileUID)) {
                continue;
            } else if (!isIdenticalFile(splitPointFileUID, givenBranchFileUID) && isIdenticalFile(splitPointFileUID, currentBranchFileUID)) {
                checkout(branches.get(branchName).peek(), fileName);
                stageFileForAddition(fileName, givenBranchFileUID);
            } else if (!isIdenticalFile(splitPointFileUID, currentBranchFileUID) && isIdenticalFile(splitPointFileUID, givenBranchFileUID)) {
                continue;
            } else if (!isIdenticalFile(splitPointFileUID, currentBranchFileUID) && !isIdenticalFile(currentBranchFileUID, givenBranchFileUID)) {
                mergeFileContents(fileName, currentBranchFileUID, givenBranchFileUID);
                mergeConflict = true;
            }
        }
        ArrayList<String> newFilesInGivenBranch = new ArrayList<>();
        for (String fileName : givenBranchCommitFiles.keySet()) {
            if (!splitPointCommitFiles.containsKey(fileName)) {
                String currentBranchFileUID = currentBranchCommitFiles.get(fileName);
                String givenBranchFileUID = givenBranchCommitFiles.get(fileName);
                if (currentBranchFileUID == null) {
                    stageFileForAddition(fileName, givenBranchFileUID);
                    newFilesInGivenBranch.add(fileName);
                } else if (!isIdenticalFile(givenBranchFileUID, currentBranchFileUID)) {
                    mergeFileContents(fileName, currentBranchFileUID, givenBranchFileUID);
                    mergeConflict = true;
                }
            }
        }
        commit("Merged " + branchName + " into " + currentBranch + ".");
        if (!newFilesInGivenBranch.isEmpty()) {
            for (String fileName : newFilesInGivenBranch) {
                overwriteCurrentFileVersion(getCurrentCommitTrackedFiles(), fileName);
            }
        }
        if (mergeConflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }
}
