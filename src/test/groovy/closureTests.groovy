def someFunc(someArg) {
	println "someFunc doing something with ${someArg}"
}

def anotherFunc(someArg) {
	println "anotherFunc doing something with ${someArg}"
}

def theFunc(test, someArg, callback) {
	println "${test} with ${someArg} calling a fucntion"
	callback(someArg)
	callback()
}

def callBack = this.&anotherFunc
theFunc("Some Test", "some arg", callBack)
