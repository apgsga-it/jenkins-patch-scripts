param(
	[String]$url
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

# TODO JHE(23.03.2018): Get username and password from somewhere else ... parameter? env variable?
$Username = 'dev'
$Password = 'dev1234'
$downloadFilePath = "${PSScriptRoot}\"
$downloadedFileName = "it21gui-dist.zip"
$downloadInProgressFileName = "it21gui-dist.zip.download"
$WebClient = New-Object System.Net.WebClient
$WebClient.Credentials = New-Object System.Net.Networkcredential($Username, $Password)

if(Test-Path "${downloadFilePath}${downloadedFileName}") {
	Write-Output "Old ${downloadFilePath}${downloadedFileName} file will be deleted..."
	Remove-Item "${downloadFilePath}${downloadedFileName}"
	Write-Output "${downloadFilePath}${downloadedFileName} deleted!"
}

Write-Output "Starting download ... (${downloadFilePath}${downloadInProgressFileName})"
$WebClient.DownloadFile( $url, "${downloadFilePath}${downloadInProgressFileName}" )

Write-Output "Download done."
Write-Output "${downloadInProgressFileName} getting renamed into ${downloadedFileName}"
Rename-Item $downloadInProgressFileName $downloadedFileName

$tempExtractFolder = getTemporaryFolderName

Write-Output "${downloadFilePath}${downloadedFileName} will be extracted to ${downloadFilePath}${tempExtractFolder}"
Expand-Archive "${downloadFilePath}${downloadedFileName}" "${downloadFilePath}${tempExtractFolder}"
Write-Output "Extraction done."
$finalFolder = getFinalFolderName
Write-Output "${tempExtractFolder} renamed into ${finalFolder}"
Rename-Item $tempExtractFolder $finalFolder
Write-Output "Renaming done, next GUI will be started from ${finalFolder}"