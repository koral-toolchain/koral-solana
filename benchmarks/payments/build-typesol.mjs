// Auxiliary benchmark only. Never imported by the Koral compiler or CLI.
import {pathToFileURL} from 'node:url';
import fs from 'node:fs';
import path from 'node:path';
const [repo,source,output,id]=process.argv.slice(2);
const compiler=await import(pathToFileURL(path.join(path.resolve(repo),'dist/compiler/index.js')).href);
const result=compiler.build(path.resolve(source),path.resolve(output),id);
fs.writeFileSync(path.join(output,'benchmark-build.json'),JSON.stringify({manifest:result.manifest,version:JSON.parse(fs.readFileSync(path.join(repo,'package.json'),'utf8')).version},null,2)+'\n');
console.log(result.soPath);
