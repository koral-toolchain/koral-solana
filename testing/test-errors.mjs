// Keep user-source locations without dumping Node loader or bundled-client frames.
import path from 'node:path';
import {fileURLToPath} from 'node:url';
const tools=path.dirname(fileURLToPath(import.meta.url));
const within=(root,file)=>{const relative=path.relative(root,file);return !path.isAbsolute(relative)&&relative!=='..'&&!relative.startsWith('..'+path.sep);};
export function formatCustomTestError(error,script) {
 const entry=path.resolve(script),project=path.dirname(path.dirname(entry));
 const message=error?.message??String(error);
 const locations=[];
 for(const line of String(error?.stack??'').split('\n')) {
  if(!/^\s+at\s/.test(line)||/(?:\(|\s)node:/.test(line))continue;
  const match=line.match(/(file:\/\/[^\n]+?|\/[^\n]+?):(\d+):(\d+)\)?$/);
  if(!match)continue;
  let file;
  try{file=path.resolve(match[1].startsWith('file:')?fileURLToPath(match[1]):match[1]);}catch{continue;}
  if(within(tools,file)||file.split(path.sep).includes('node_modules'))continue;
  const display=within(project,file)?path.relative(project,file):file;
  const location=`${display}:${match[2]}:${match[3]}`;
  if(!locations.includes(location))locations.push(location);
  if(locations.length===3)break;
 }
 const detail=locations.length?locations.map(v=>'  at '+v).join('\n'):`  Test entry: ${path.relative(project,entry)} (source location unavailable)`;
 return `Koral: Custom test failed: ${message}\n${detail}`;
}
