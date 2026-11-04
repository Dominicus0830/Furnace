const { makeBot, sleep } = require('./lib');
(async () => {
  const b = makeBot('Packer', { plugins: { resource_pack: false } }); const t = Date.now(); const ts = () => ((Date.now() - t) / 1000).toFixed(1);
  b._client.removeAllListeners('add_resource_pack');
  console.log('listeners now', b._client.listenerCount('add_resource_pack'));
  b._client.on('add_resource_pack', async p => { console.log(ts(), 'add_resource_pack', b._client.state, 'listeners', b._client.listenerCount('add_resource_pack')); b._client.write('resource_pack_receive', { uuid: p.uuid, result: 3 }); console.log(ts(), 'sent 3'); await sleep(40000); b._client.write('resource_pack_receive', { uuid: p.uuid, result: 0 }); console.log(ts(), 'sent 0'); });
  b._client.on('finish_configuration', () => console.log(ts(), 'finish_configuration 받음'));
  b._client.on('login', () => console.log(ts(), 'login(play) 받음'));
  b._client.on('keep_alive', () => console.log(ts(), 'keep_alive'));
  b.on('end', () => console.log(ts(), 'end'));
  await sleep(50000); b.quit(); await sleep(300); process.exit(0);
})();
