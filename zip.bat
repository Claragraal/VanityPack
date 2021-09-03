@echo off
FOR /D %%i in (*) do (
	set "TRUE="
	IF %%i==ResPacker set TRUE=1
	IF %%i==output set TRUE=1
	IF %%i==temp set TRUE=1
	IF defined TRUE (
		echo skipping %%i
	) else (
		echo Zipping directory '%%i'
		cd temp/%%i
		tar.exe -a -c -f ./../../output/%%i.zip assets pack.png pack.mcmeta
		cd ../..
		cd output
		certutil -hashfile %%i.zip SHA1
		cd ..
	)
)