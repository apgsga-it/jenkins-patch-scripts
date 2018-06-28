def cli = new CliBuilder(usage: '-j|jenkins <directory>')
cli.with {
	j longOpt: 'jenkins', 'Jenkins installation directory', required: true
	u longOpt: 'update', 'If to run with updates', required: false
}
def options = cli.parse(args)
if (options == null) {
	System.exit(1)
}
def directory = new File(options.j)
if (!directory.exists() | !directory.directory) {
	println "Directory ${options.l} not valid: either not a directory or it doesn't exist"
	System.exit(1)
}
def dry = options.u == null
println "Running with Updates : ${options.u}"