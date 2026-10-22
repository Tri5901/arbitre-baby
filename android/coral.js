// Exécuté par l'appli Android dans la page publique Coral du tournoi (app.tablesoccer.org/p/CODE),
// chargée en arrière-plan comme le ferait un spectateur. Lit le store Vue de la page, ne modifie rien.
// __CODE__ et __TABLE__ sont remplacés par l'appli. Renvoie null tant que la page n'est pas prête.
(function(){
  var CODE = __CODE__, TABLE = __TABLE__;
  var app = document.querySelector('#q-app');
  var st = app && app.__vue__ && app.__vue__.$store && app.__vue__.$store.state.tournament;
  if (!st || !st.tournament || String(st.tournament.code).toUpperCase() !== CODE) return null;
  if (!st.matches || !st.playgrounds || !Object.keys(st.matches).length) return null;

  var base = { tournoi: st.tournament.name, code: CODE, table: TABLE };
  var pg = null, nums = [];
  Object.keys(st.playgrounds).forEach(function(k){
    var p = st.playgrounds[k];
    nums.push(p.number);
    if (String(p.number) === String(TABLE)) pg = p;
  });
  if (!pg) { base.err = 'table'; base.tables = nums.sort(function(a, b){ return a - b; }); return base; }

  // En cours d'abord, puis appelé (« lancé »), puis à venir
  var rang = { playing: 0, launched: 1, created: 2 }, ms = [];
  Object.keys(st.matches).forEach(function(i){
    var m = st.matches[i];
    if (m.playground_id === pg.id && rang[m.status] !== undefined) ms.push(m);
  });
  if (!ms.length) { base.err = 'match'; return base; }
  ms.sort(function(a, b){ return rang[a.status] - rang[b.status] || String(a.start_at || '').localeCompare(String(b.start_at || '')); });
  var m = ms[0];

  function equipe(id){
    var l = (st.team_players_index && st.team_players_index[id]) || [];
    return l.map(function(pid){ var p = st.players[pid]; return p ? { prenom: p.first_name, nom: p.last_name } : null; })
      .filter(function(x){ return x; });
  }
  var fmts = (st.taxonomies && st.taxonomies.match_formats) || [];
  function format(id){ for (var i = 0; i < fmts.length; i++) if (fmts[i].id === id) return fmts[i]; return null; }
  var ph = st.phases[m.phase_id] || {};
  var comp = ph.competition && st.competitions && st.competitions[ph.competition.id];
  var f = ph.match_format && format(ph.match_format.id), bo = 1, belle = f;
  // Format en manches : nombre de manches, et règle de la manche décisive (souvent « jusqu'à 8 » ou « 2 buts d'écart »)
  if (f && f.type === 'games') {
    bo = f.settings.games_count || 3;
    var ov = (f.settings.games_match_format_overrides || []).filter(function(o){ return o.game === bo; })[0];
    belle = format(ov ? ov.match_format.id : (f.settings.games_match_format_default || {}).id);
  }
  var r = (belle && belle.settings) || {};
  base.match = {
    id: m.id, statut: m.status, comp: comp ? comp.name : '', phase: ph.name || '',
    home: equipe(m.home), away: equipe(m.away), bo: bo, format: f ? f.name : '',
    regle: { win: r.points_win || 5, diff: r.points_diff || 2, max: r.points_max === undefined ? 8 : r.points_max }
  };
  return base;
})()
