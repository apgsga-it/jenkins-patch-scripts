# Preconditions for executing Jenkins Client API scripts

#### Java Security Certification

For executing HTTP requests with a jvm client (groovy, java eetc) against HTTP you need to set up Java Certs

+ Go to URL in your firefox browser, click on HTTPS certificate chain (next to URL address). Click "more info" > "security" > "show certificate" > "details" > "export.". Pickup the name and choose a file for example mavenrepoapgsgach.crt and save the file somewhere. 
+ Determine location of your Java ca certs files for the Jdk your using, eg. for me it is: C:\Program Files\Java\jdk1.8.0_91\jre\lib\security
+ Move the exported file to there for convenience und change the directory to there
+ Import the file with the java key tool via a Shell with administrator rights
 	
 		`keytool -import -alias jenkinsserver -keystore cacerts -file mavenrepoapgsgach.crt`
 	
 
+ You will be asked for a password for which the default is *changeit*
 
You will have to do this everytime the cert of the jenkins server changes.

Problems, which may occur: 

No Admin rights, you need to have administrator right to change to keystore

The same alias exists already -> change the alias name

You need to confirm, that you want to change to keystore.

Additional information, see: <https://stackoverflow.com/questions/21076179/pkix-path-building-failed-and-unable-to-find-valid-certification-path-to-requ> and <http://magicmonster.com/kb/prg/java/ssl/pkix_path_building_failed.html>

#### Jenkins Authentication 
 
 You use with Basic HTTP Authentication your user name you login with and a so called Jenkins API Token. 
 
You will find that API Token in your personal "Einstellungen" in Jenkins. When you are logged in, on arbitrary page on https://jenkins.apgsga.ch, you will find to the top right your Name. 

Click on your name -> Eintstellungen -> Api-Token 

This token you use as password for basic Authentication

#### Jenkins Crumb for Security

Additionally you need to pass a "Jenkins-Crumb" Value in the Request Header for a Http request.

The Crumb Value will be something like: 8b260771a65ed949fa7e4a4ea3875a90

You have the crumb Value displayed with the wget script, executing the /jenkins-patch-scripts/client/scripts/wgetjenkinscrumb.sh script. 

It seems that the crumb is generated newly with each restart.

#### Groovy configuration

Grape is the Grooy evquivalent to Mavens dependency management, see <http://docs.groovy-lang.org/latest/html/documentation/grape.html> and <https://github.com/apache/groovy/blob/master/src/resources/groovy/grape/defaultGrapeConfig.xmlhttps://github.com/apache/groovy/blob/master/src/resources/groovy/grape/defaultGrapeConfig.xml>

To specify required artifact dependency, you have something like: 

    @Grapes([
		@Grab(group='commons-httpclient', module='commons-httpclient', version='3.1'),
		@Grab( 'com.google.code.gson:gson:2.8.1' )
	]) 

If Maven has been configured with a "non-standard" repository location, meaning not in your home directory .m2 directory, one could have problems to download the required resources for Groovy. This might required a Grape configuration. In order to do it, you can create a grapeConfig.xml file within the ${user.home}/.groovy folder. Content of the file should then be with the *localm2* key showing to your local repository


    <ivysettings>
       <settings defaultResolver="downloadGrapes"/>
       <resolvers>
	      <chain name="downloadGrapes" returnFirst="true">
	       <filesystem name="cachedGrapes">
		    <ivy pattern="${user.home}/.groovy/grapes/[organisation]/[module]/ivy-[revision].xml"/>
		    <artifact pattern="${user.home}/.groovy/grapes/[organisation]/[module]/[type]s/[artifact]-[revision](-[classifier]).[ext]"/>
	      </filesystem>
	      <ibiblio name="localm2" root="file:C:/tools/maven/mavenLocalRepo/" checkmodified="true" changingPattern=".*" changingMatcher="regexp" m2compatible="true" usepoms="false"/>
	      <ibiblio name="jcenter" root="https://jcenter.bintray.com/" m2compatible="true"/>
	      <ibiblio name="ibiblio" m2compatible="true"/>
	    </chain>
      </resolvers>
    </ivysettings>
    
This is a quick fix, naturally we could/should also change the remote repository settings to those of our maven settings.xml.



#### Scripts

TODO 
	
	
### TODO's

- [X] Command Line parameters for the scripts. The parameters are hardcoded
- [X] Add parameters for Jenkins Job invocation
- [x] Integrate getting JenkinsCrumb dynamically into the scripts



