def cli = new CliBuilder(usage: '-j|jenkins <directory>')
cli.with {
	j longOpt: 'jenkins', 'Jenkins installation directory', required: true
	u longOpt: 'update', 'If to run with updates', required: false
}
def options = cli.parse(args)
if (options == null) {
	return System.exit(1)
}
def dry = options.u == null
println "Running with Updates : ${options.u}"