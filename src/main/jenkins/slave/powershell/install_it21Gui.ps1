param(
	[parameter(Mandatory=$true)]
	[String]
	$url,
	
	[parameter(Mandatory=$true)]
	[String]
	$target
)

function getFinalFolderName() {
	$dateAsString = getCurrentDatetimeformated
	return "java_gui_${dateAsString}"
}

function getTemporaryFolderName() {
	$dateAsString = getCurrentDatetimeformated
	return "java_gui_getting_extracted_${dateAsString}"
}

function getCurrentDatetimeformated() {
	return Get-Date -format "yyyyMMddHHmmss"
}

Write-Output "install_it21Gui script started!"

# TODO JHE(23.03.2018): Get username and password from somewhere else ... parameter? env variable?
$Username = 'dev'
$Password = 'dev1234'
#$downloadFilePath = "${PSScriptRoot}\"
$downloadFilePath = "C:\Software\it21shares\it21_${target}\"
$downloadedFileName = "${downloadFilePath}it21gui-dist.zip"
$downloadInProgressFileName = "${downloadFilePath}it21gui-dist.zip.download"
$WebClient = New-Object System.Net.WebClient
$WebClient.Credentials = New-Object System.Net.Networkcredential($Username, $Password)

if(Test-Path "${downloadedFileName}") {
	Write-Output "Old ${downloadedFileName} file will be deleted..."
	Remove-Item "${downloadedFileName}"
	Write-Output "${downloadedFileName} deleted!"
}

Write-Output "Starting download ... (${downloadInProgressFileName})"
$WebClient.DownloadFile( $url, "${downloadInProgressFileName}" )

Write-Output "Download done."
Write-Output "${downloadInProgressFileName} getting renamed into ${downloadedFileName}"
Rename-Item $downloadInProgressFileName $downloadedFileName

$tempExtractFolder = getTemporaryFolderName

Write-Output "${downloadedFileName} will be extracted to ${downloadFilePath}${tempExtractFolder}"
Expand-Archive "${downloadedFileName}" "${downloadFilePath}${tempExtractFolder}"
Write-Output "Extraction done."
$finalFolder = getFinalFolderName
Write-Output "${tempExtractFolder} renamed into ${finalFolder}"
Rename-Item "${downloadFilePath}\${tempExtractFolder}" "${downloadFilePath}\${finalFolder}"
Write-Output "Renaming done, next GUI will be started from ${finalFolder}"

Write-Output "install_it21Gui script finished!"