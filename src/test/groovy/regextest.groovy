def jobName = "Patch98x"
def match = (jobName  ==~ /Patch+[0123456789]*/)
println match
