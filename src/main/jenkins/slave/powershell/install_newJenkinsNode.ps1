param(
	
	[parameter(Mandatory=$true)]
    [String]
    $nodeName,
    
	[parameter(Mandatory=$true)]
	[String]
	$jnlpUrl,
	
	[parameter(Mandatory=$true)]
	[String]
	$secret,
	
	[parameter(Mandatory=$true)]
	[String]
	$workDir,
	
	[parameter(Mandatory=$true)]
	[String]
	$sharesHomeFolder,
	
	[parameter(Mandatory=$true)]
	[String]
	$slavesHomeFolder
)

New-Item -ItemType directory -Path "${slavesHomeFolder}\${nodeName}"
Expand-Archive "winAgent.zip" "${slavesHomeFolder}\${nodeName}"
Rename-Item "${slavesHomeFolder}\${nodeName}\JenkinsWinAgent.exe" "${slavesHomeFolder}\${nodeName}\JenkinsWinAgent${nodeName}.exe"
Rename-Item "${slavesHomeFolder}\${nodeName}\JenkinsWinAgent.xml" "${slavesHomeFolder}\${nodeName}\JenkinsWinAgent${nodeName}.xml"
Rename-Item "${slavesHomeFolder}\${nodeName}\JenkinsWinAgent.exe.config" "${slavesHomeFolder}\${nodeName}\JenkinsWinAgent${nodeName}.exe.config"

(Get-Content "${slavesHomeFolder}\${nodeName}\JenkinsWinAgent${nodeName}.xml").replace('[env]', "${nodeName}") | Set-Content "${slavesHomeFolder}\${nodeName}\JenkinsWinAgent${nodeName}.xml"
(Get-Content "${slavesHomeFolder}\${nodeName}\JenkinsWinAgent${nodeName}.xml").replace('[jnlpUrl]', "${jnlpUrl}") | Set-Content "${slavesHomeFolder}\${nodeName}\JenkinsWinAgent${nodeName}.xml"
(Get-Content "${slavesHomeFolder}\${nodeName}\JenkinsWinAgent${nodeName}.xml").replace('[secret]', "${secret}") | Set-Content "${slavesHomeFolder}\${nodeName}\JenkinsWinAgent${nodeName}.xml"
(Get-Content "${slavesHomeFolder}\${nodeName}\JenkinsWinAgent${nodeName}.xml").replace('[workdir]', "${workDir}") | Set-Content "${slavesHomeFolder}\${nodeName}\JenkinsWinAgent${nodeName}.xml"

Expand-Archive "startScripts.zip" "${sharesHomeFolder}\it21_${nodeName}"

$serviceInstaller = "${slavesHomeFolder}\${nodeName}\JenkinsWinAgent${nodeName}.exe"
& $serviceInstaller "uninstall"
& $serviceInstaller "install"

