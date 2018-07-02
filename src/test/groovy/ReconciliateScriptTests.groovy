import org.junit.Assert
import org.junit.Test

class ReconciliateScriptTests extends GroovyTestCase {
	
	static def TEST_DIR_NAME = "src/test/resources/test"


	@Test
	void testNoArgumentsMissingOption() {
		Binding binding = new Binding()
		binding.args = []
		GroovyShell shell = new GroovyShell(binding)
		def script = shell.parse(new File('src/main/groovy/reconciliateWorkspaces.groovy'))
		def outWriter = new StringWriter()
		script.out = new PrintWriter(outWriter)
		script.run()
		println "Output: " + outWriter.toString()
		assertTrue "Expected Error Message", outWriter.toString().contains("error: Missing required option: j")
	
	}
	
	@Test
	void testMissingDirectoryOption() {
		Binding binding = new Binding()
		binding.args = ["-j"]
		GroovyShell shell = new GroovyShell(binding)
		def script = shell.parse(new File('src/main/groovy/reconciliateWorkspaces.groovy'))
		def outWriter = new StringWriter()
		script.out = new PrintWriter(outWriter)
		script.run()
		println "Output: " + outWriter.toString()
		assertTrue "Expected Error Message", outWriter.toString().contains("error: Missing argument for option: j")
	
	}
	
	@Test
	void testDirectoryNotExisting() {
		Binding binding = new Binding()
		binding.args = ["-j", "test"]
		GroovyShell shell = new GroovyShell(binding)
		def script = shell.parse(new File('src/main/groovy/reconciliateWorkspaces.groovy'))
		def outWriter = new StringWriter()
		script.out = new PrintWriter(outWriter)
		script.run()
		println "Output: " + outWriter.toString()
		assertTrue "Expected Error Message", outWriter.toString().contains("error: Directory test not valid: either not a directory or it doesn't exist")
	
	}
	
	@Test
	void testDirectoryExistsButNotJenkins() {
		def testDir = new File(TEST_DIR_NAME)
		testDir.deleteDir()
		testDir.mkdir()
		Binding binding = new Binding()
		binding.args = ["-j", TEST_DIR_NAME]
		GroovyShell shell = new GroovyShell(binding)
		def script = shell.parse(new File('src/main/groovy/reconciliateWorkspaces.groovy'))
		def outWriter = new StringWriter()
		script.out = new PrintWriter(outWriter)
		script.run()
		println "Output: " + outWriter.toString()
		assertTrue "Expected Error Message", outWriter.toString().contains("error: Does'nt seem to be a Jenkins installation, no jobs subdirectory")
	
	}
	
	@Test
	void testDirectoryExistsButNotJenkinsII() {
		def testDir = new File(TEST_DIR_NAME)
		testDir.deleteDir()
		testDir.mkdir()
		def jobsDir = new File(testDir,"jobs")
		jobsDir.mkdir()
		Binding binding = new Binding()
		binding.args = ["-j", TEST_DIR_NAME]
		GroovyShell shell = new GroovyShell(binding)
		def script = shell.parse(new File('src/main/groovy/reconciliateWorkspaces.groovy'))
		def outWriter = new StringWriter()
		script.out = new PrintWriter(outWriter)
		script.run()
		println "Output: " + outWriter.toString()
		assertTrue "Expected  Message", outWriter.toString().contains("error: Does'nt seem to be a Jenkins installation, no workspace subdirectory")
	
	}
	
	@Test
	void testEmpty() {
		def testDir = new File(TEST_DIR_NAME)
		testDir.deleteDir()
		testDir.mkdir()
		def jobsDir = new File(testDir,"jobs")
		jobsDir.mkdir()
		def wsDir = new File(testDir,"workspace")
		wsDir.mkdir()
		Binding binding = new Binding()
		binding.args = ["-j", TEST_DIR_NAME]
		GroovyShell shell = new GroovyShell(binding)
		def script = shell.parse(new File('src/main/groovy/reconciliateWorkspaces.groovy'))
		def outWriter = new StringWriter()
		script.out = new PrintWriter(outWriter)
		script.run()
		println "Output: " + outWriter.toString()
		assertTrue outWriter.toString().contains("Running with Updates : false \nCleaning up workspaces in : src/test/resources/test/workspace ")

	
	}
	
	@Test
	void testJobWsNotJobsDry() {
		def testDir = new File(TEST_DIR_NAME)
		testDir.deleteDir()
		testDir.mkdir()
		def jobsDir = new File(testDir,"jobs")
		jobsDir.mkdir()
		def wsDir = new File(testDir,"workspace")
		wsDir.mkdir()
		def jobwsDir = new File(wsDir,"testjob")
		jobwsDir.mkdir()
		Binding binding = new Binding()
		binding.args = ["-j", TEST_DIR_NAME]
		GroovyShell shell = new GroovyShell(binding)
		def script = shell.parse(new File('src/main/groovy/reconciliateWorkspaces.groovy'))
		def outWriter = new StringWriter()
		script.out = new PrintWriter(outWriter)
		script.run()
		println "Output: " + outWriter.toString()
		assertTrue outWriter.toString().contains("Directory testjob can be deleted")
		assertTrue outWriter.toString().contains("Running dry testjob not deleted")
		assertTrue jobwsDir.exists()

	
	}
	
	@Test
	void testJobWsNotJobsNotDry() {
		def testDir = new File(TEST_DIR_NAME)
		testDir.deleteDir()
		testDir.mkdir()
		def jobsDir = new File(testDir,"jobs")
		jobsDir.mkdir()
		def wsDir = new File(testDir,"workspace")
		wsDir.mkdir()
		def jobwsDir = new File(wsDir,"testjob")
		jobwsDir.mkdir()
		Binding binding = new Binding()
		binding.args = ["-j", TEST_DIR_NAME, "-u"]
		GroovyShell shell = new GroovyShell(binding)
		def script = shell.parse(new File('src/main/groovy/reconciliateWorkspaces.groovy'))
		def outWriter = new StringWriter()
		script.out = new PrintWriter(outWriter)
		script.run()
		println "Output: " + outWriter.toString()
		assertTrue outWriter.toString().contains("Directory testjob can be deleted")
		assertTrue outWriter.toString().contains("testjob has been deleted")
		assertTrue !jobwsDir.exists()
		assertTrue jobsDir.exists()
		assertTrue wsDir.exists()

	
	}
	
	@Test
	void testJobWsNotJobsNotDryII() {
		def testDir = new File(TEST_DIR_NAME)
		testDir.deleteDir()
		testDir.mkdir()
		def jobsDir = new File(testDir,"jobs")
		jobsDir.mkdir()
		def wsDir = new File(testDir,"workspace")
		wsDir.mkdir()
		def jobwsDir = new File(wsDir,"testjob")
		jobwsDir.mkdir()
		def jobwsDir2 = new File(wsDir,"testjob@one")
		jobwsDir2.mkdir()
		Binding binding = new Binding()
		binding.args = ["-j", TEST_DIR_NAME, "-u"]
		GroovyShell shell = new GroovyShell(binding)
		def script = shell.parse(new File('src/main/groovy/reconciliateWorkspaces.groovy'))
		def outWriter = new StringWriter()
		script.out = new PrintWriter(outWriter)
		script.run()
		println "Output: " + outWriter.toString()
		assertTrue outWriter.toString().contains("Directory testjob can be deleted")
		assertTrue outWriter.toString().contains("testjob has been deleted")
		assertTrue !jobwsDir.exists()
		assertTrue jobsDir.exists()
		assertTrue wsDir.exists()

	
	}
}
