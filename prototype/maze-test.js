// 抽出 index.html 里的迷宫生成逻辑，验证三条不变量
const SUFFIX = ['钟','酱','汁','派','小组','盒子','计划','侠','日记','森林','工厂','小站','手册','清单','们'];
const PREFIX = ['小','老','大','新','我的','那个','真','假'];
const FILLER = ['西红柿','土豆','白菜','洋葱','南瓜','青椒','香菇','冬瓜','芹菜','生菜','豆腐','茄子'];

function decoys(kw, n){
  const pool = new Set();
  SUFFIX.forEach(s => pool.add(kw + s));
  PREFIX.forEach(p => pool.add(p + kw));
  if (kw.length >= 2){
    pool.add(kw.slice(0, -1));
    pool.add(kw.slice(1));
    pool.add(kw[0] + kw[kw.length-1]);
  }
  if (kw.length >= 3) pool.add(kw[0] + kw.slice(2));
  FILLER.forEach(f => pool.add(f));
  pool.delete(kw);
  const arr = [...pool];
  for (let i = arr.length - 1; i > 0; i--){
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr.slice(0, n);
}

function buildLayer(kw, count){
  const pathPos = Math.floor(Math.random() * count);
  const d = decoys(kw, count - 1);
  let di = 0;
  return Array.from({length: count}, (_, i) =>
    i === pathPos ? { text: kw, isPath: true } : { text: d[di++], isPath: false }
  );
}

let fail = 0;
function check(name, cond, extra=''){
  if (!cond){ fail++; console.log(`  FAIL  ${name} ${extra}`); }
}

// 各种长度的口令词都要能扛住（1字、2字、3字、4字）
const keywords = ['番茄','番茄钟','拖延症','学习','英语单词','a','代码'];
const counts = [4,6,9,12,16];

console.log('--- 不变量测试 ---');
for (const kw of keywords){
  for (const c of counts){
    for (let run = 0; run < 200; run++){
      const layer = buildLayer(kw, c);

      // 1) 格子数量正确
      check('长度', layer.length === c, `kw=${kw} c=${c} got=${layer.length}`);

      // 2) 有且仅有一个路径格
      const paths = layer.filter(x => x.isPath).length;
      check('唯一路径', paths === 1, `kw=${kw} c=${c} paths=${paths}`);

      // 3) 路径格的文字 == 口令词本身
      const p = layer.find(x => x.isPath);
      check('路径文字', p && p.text === kw, `kw=${kw} got=${p && p.text}`);

      // 4) 干扰格文字绝不等于口令词（否则玩家永远分不清真假）
      layer.filter(x => !x.isPath).forEach(x => {
        check('干扰不撞车', x.text !== kw, `kw=${kw} 撞车=${x.text}`);
      });

      // 5) 同一层内干扰格不重复（否则一眼看到重复就知道有问题）
      const texts = layer.filter(x => !x.isPath).map(x => x.text);
      check('干扰不重复', new Set(texts).size === texts.length, `kw=${kw} c=${c}`);
    }
  }
}
console.log(fail === 0 ? '  ✓ 全部通过（7 个口令词 × 5 种格子数 × 200 轮）' : `  ✗ ${fail} 处失败`);

// 干扰词池容量检查：口令词很短时池子够不够用
console.log('\n--- 干扰词池容量 ---');
for (const kw of ['a','番茄','番茄钟']){
  const pool = new Set();
  SUFFIX.forEach(s => pool.add(kw + s));
  PREFIX.forEach(p => pool.add(p + kw));
  if (kw.length >= 2){ pool.add(kw.slice(0,-1)); pool.add(kw.slice(1)); pool.add(kw[0]+kw[kw.length-1]); }
  if (kw.length >= 3) pool.add(kw[0]+kw.slice(2));
  FILLER.forEach(f => pool.add(f));
  pool.delete(kw);
  console.log(`  「${kw}」可造 ${pool.size} 个干扰词，最大需求 15 个 → ${pool.size >= 15 ? '够用' : '不足！'}`);
}

// 展示真实一层长什么样
console.log('\n--- 真实一层（口令词「番茄」，9 格）---');
buildLayer('番茄', 9).forEach((s,i) =>
  console.log(`  [${i}] ${s.isPath ? '★' : ' '} ${s.text}`));
