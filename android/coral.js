// Exécuté par l'appli Android dans la page publique Coral du tournoi (app.tablesoccer.org/p/CODE),
// chargée en arrière-plan comme le ferait un spectateur. Lit le store Vue de la page, ne modifie rien.
// __CODE__ et __TABLE__ sont remplacés par l'appli. Renvoie null tant que la page n'est pas prête.
// Copie servie aussi par le site (coral.js à la racine) : l'APK 4+ la télécharge, donc la modifier ne demande plus de refaire l'APK.
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

  // En cours d'abord, puis appelé (« lancé »), puis à venir.
  // Un match multi-tables occupe deux tables (playground_id et playground2_id) : on le trouve depuis l'une ou l'autre.
  var rang = { playing: 0, launched: 1, created: 2 }, ms = [];
  Object.keys(st.matches).forEach(function(i){
    var m = st.matches[i];
    if ((m.playground_id === pg.id || m.playground2_id === pg.id) && rang[m.status] !== undefined) ms.push(m);
  });
  if (!ms.length) { base.err = 'match'; return base; }
  ms.sort(function(a, b){ return rang[a.status] - rang[b.status] || String(a.start_at || '').localeCompare(String(b.start_at || '')); });
  var m = ms[0];

  function joueur(pid){ var p = st.players[pid]; return p ? { prenom: p.first_name, nom: p.last_name } : null; }
  function present(x){ return x; }
  function equipe(id){
    var l = (st.team_players_index && st.team_players_index[id]) || [];
    return l.map(joueur).filter(present);
  }
  function nomEquipe(id){ var t = st.teams && st.teams[id]; return t ? (t.rename || t.name || '') : ''; }
  function table(id){
    var p = id && st.playgrounds[id];
    return p ? { num: p.number, type: (p.type && p.type.name) || p.name || '' } : null;
  }
  var fmts = (st.taxonomies && st.taxonomies.match_formats) || [];
  function format(id){ for (var i = 0; i < fmts.length; i++) if (fmts[i].id === id) return fmts[i]; return null; }
  var cats = (st.taxonomies && st.taxonomies.team_categories) || [];
  function nbJoueurs(catId){ for (var i = 0; i < cats.length; i++) if (cats[i].id === catId) return cats[i].player_count || 1; return 1; }

  var ph = st.phases[m.phase_id] || {};
  var comp = ph.competition && st.competitions && st.competitions[ph.competition.id];
  var f = ph.match_format && format(ph.match_format.id), bo = 1, belle = f;
  var fs = (f && f.settings) || {};
  // Format en manches : nombre de manches, et règle de la manche décisive (souvent « jusqu'à 8 » ou « 2 buts d'écart »)
  if (f && f.type === 'games') {
    bo = fs.games_count || 3;
    var ov = (fs.games_match_format_overrides || []).filter(function(o){ return o.game === bo; })[0];
    belle = format(ov ? ov.match_format.id : (fs.games_match_format_default || {}).id);
  }
  var r = (belle && belle.settings) || {};

  // Relais : paliers (D1 à 6, D2 à 12…), pause entre éléments, temps morts, nul possible
  var relais = null, compo = [];
  if (f && f.type === 'relay') {
    var paliers = (fs.relay_rounds || []).map(function(x){
      return { val: x.value, cat: (x.team_category && x.team_category.name) || '', nb: nbJoueurs(x.team_category && x.team_category.id) };
    });
    // ecart = écart minimal pour gagner le dernier élément (relay_min_win_points_diff), pas points_diff des manches
    relais = { paliers: paliers, pause: fs.relay_break_time || 0, tm: fs.timeout_count || 0, tmDuree: fs.timeout_duration || 0,
      tmType: fs.timeout_type || '', nul: fs.relay_draw || 0, ecart: fs.relay_min_win_points_diff || 0, max: fs.relay_max_win_points_limit || 0 };
    // Composition : match.lineups = {home:[ids], away:[ids]} à plat, découpé dans l'ordre des paliers
    // (même découpage que playersRelayLineup de Coral). Vide tant que les équipes ne l'ont pas déposée.
    if (m.lineups && m.lineups.home && m.lineups.away) {
      var k = 0;
      paliers.forEach(function(p){
        compo.push({ home: m.lineups.home.slice(k, k + p.nb).map(joueur).filter(present),
                     away: m.lineups.away.slice(k, k + p.nb).map(joueur).filter(present) });
        k += p.nb;
      });
    }
  }

  var tables = [table(m.playground_id), table(m.playground2_id)].filter(present);
  base.match = {
    id: m.id, statut: m.status, comp: comp ? comp.name : '', phase: ph.name || '',
    home: equipe(m.home), away: equipe(m.away), bo: bo, format: f ? f.name : '', type: f ? f.type : '',
    regle: { win: r.points_win || 5, diff: r.points_diff || 2, max: r.points_max === undefined ? 8 : r.points_max },
    tm: fs.timeout_count || 0, tmDuree: fs.timeout_duration || 0,
    tables: tables, clubs: [nomEquipe(m.home), nomEquipe(m.away)],
    relais: relais, compo: compo
  };
  return base;
})()
