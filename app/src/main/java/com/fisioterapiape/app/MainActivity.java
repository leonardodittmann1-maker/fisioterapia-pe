package com.fisioterapiape.app;

import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout root, page;
    private int ink = Color.rgb(27,44,61), teal = Color.rgb(15,118,110);
    private final String[] phases = {"1–2: Mobilidade leve", "3–4: Ativação", "5–8: Força e estabilidade", "9–12: Resistência"};
    private final String[][] plans = {
        {"Flexão e extensão do tornozelo sem carga|2 séries de 10 movimentos lentos; sem forçar a região dolorida.",
         "Abrir e fechar os dedos|2 séries de 10; interromper se provocar pontada."},
        {"Ativação do arco (short foot)|2 séries de 8; segurar 5 segundos; 4 dias/semana.",
         "Isometria leve de inversão|2 séries de 8; segurar 5 segundos; 3 dias/semana.",
         "Elevar calcanhares sentada|2 séries de 10; 3 dias/semana."},
        {"Inversão com faixa elástica leve|3 séries de 12; 3 dias/semana.",
         "Elevação dos dois calcanhares em pé|2 séries de 8 a 12; 3 dias/semana.",
         "Equilíbrio apoiado em um pé|3 séries de 20 segundos; com apoio próximo."},
        {"Elevação unilateral de calcanhar|2 séries de 6 a 10; 2 a 3 dias/semana.",
         "Passos laterais com faixa|2 séries de 10 passos para cada lado; 3 dias/semana.",
         "Caminhada gradual|Começar com duração que não cause piora nas próximas 24 horas."}
    };
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("fisio", MODE_PRIVATE);
        show("inicio");
    }
    private TextView txt(String s, int size, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(ink);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        t.setPadding(0, 8, 0, 8);
        return t;
    }
    private void add(String s, int size, boolean bold) { page.addView(txt(s,size,bold)); }
    private Button button(String name, Runnable action) {
        Button b = new Button(this);
        b.setText(name); b.setAllCaps(false); b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(teal));
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void card(String heading, String description) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(20,14,20,14);
        wrap.setBackgroundColor(Color.rgb(239,247,245));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.setMargins(0,8,0,8);
        page.addView(wrap,lp);
        wrap.addView(txt(heading,17,true));
        wrap.addView(txt(description,14,false));
    }
    private void show(String screen) {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);
        TextView title = txt("Meu Pé • Fisio", 25, true);
        title.setTextColor(teal); title.setPadding(24,25,16,12); root.addView(title);
        HorizontalScrollView nav = new HorizontalScrollView(this);
        LinearLayout tabs = new LinearLayout(this); tabs.setOrientation(LinearLayout.HORIZONTAL);
        String[] names={"Início","Exercícios","Registrar","Histórico","Ajustes"};
        String[] keys={"inicio","exercicios","registrar","historico","ajustes"};
        for (int i=0;i<names.length;i++) {
            final String key=keys[i];
            Button tab=new Button(this); tab.setText(names[i]); tab.setAllCaps(false);
            tab.setOnClickListener(v->show(key)); tabs.addView(tab);
        }
        nav.addView(tabs);root.addView(nav);
        ScrollView scroll = new ScrollView(this);
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(24,14,24,48);
        scroll.addView(page);root.addView(scroll);
        if (screen.equals("inicio")) home();
        if (screen.equals("exercicios")) exercises();
        if (screen.equals("registrar")) register();
        if (screen.equals("historico")) history();
        if (screen.equals("ajustes")) settings();
    }
    private void home() {
        add("Seu acompanhamento diário",22,true);
        card("Segurança primeiro","A causa da dor no navicular ainda não foi confirmada. Antes de exercícios com carga, procure avaliação para descartar fratura por estresse ou outra lesão óssea.");
        int phase=prefs.getInt("phase",0);
        add("Fase selecionada: "+phases[phase],18,true);
        add("Rotina orientativa, não uma prescrição. Avance apenas com liberação profissional e sem crises após esforço.",14,false);
        page.addView(button("Ver exercícios de hoje",()->show("exercicios")));
        page.addView(button("Registrar dor e atividade",()->show("registrar")));
        if (prefs.getBoolean("limping",false))
            card("Atenção: última crise com mancar","Não aumente os exercícios. Se a dor for intensa, persistente ou impedir apoiar o pé, procure avaliação médica.");
        try {
            JSONArray arr=new JSONArray(prefs.getString("records","[]"));
            add("Registros feitos: "+arr.length(),17,true);
            if(arr.length()>0){JSONObject last=arr.getJSONObject(arr.length()-1);
                card("Último registro",last.optString("date")+" • Dor "+last.optInt("pain")+"/10 • "+last.optString("note"));}
        }catch(Exception ignored){}
    }
    private void exercises() {
        add("Exercícios guiados",22,true);
        boolean cleared=prefs.getBoolean("cleared",false);
        card(cleared?"Liberação registrada":"Exercícios com carga bloqueados",
             cleared?"Respeite seus limites e as orientações do fisioterapeuta.":"Confirme com um profissional que exercícios de fortalecimento e com carga são adequados. Os exercícios abaixo são apenas informativos.");
        Spinner selector = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,phases);
        selector.setAdapter(adapter);selector.setSelection(prefs.getInt("phase",0));
        page.addView(selector);
        page.addView(button("Selecionar esta fase",()->{
            int p=selector.getSelectedItemPosition();
            if(p>0&&!prefs.getBoolean("cleared",false)) {
                Toast.makeText(this,"É necessária liberação profissional para esta fase.",Toast.LENGTH_LONG).show();return;
            }
            prefs.edit().putInt("phase",p).apply();show("exercicios");
        }));
        int phase=prefs.getInt("phase",0);
        add("Programa da fase "+phases[phase],18,true);
        for (String exercise:plans[phase]) {
            String[] parts=exercise.split("\\|",2);
            card(parts[0],parts[1]+ "\n\nSe causar dor pontual, pare. Não use a melhora após 'aquecer' como sinal de segurança.");
        }
        if(phase==0)add("Fase inicial: exercícios leves, sem resistência ou carga. Interrompa se houver dor.",14,false);
        page.addView(button("Concluir sessão e registrar",()->show("registrar")));
    }
    private void register() {
        add("Registrar a evolução",22,true);
        add("Intensidade da dor agora (0 a 10)",17,true);
        TextView number=txt("0/10",18,true); page.addView(number);
        SeekBar seek=new SeekBar(this);seek.setMax(10);page.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){number.setText(progress+"/10");}
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });
        add("Minutos caminhando ou em pé (opcional)",15,true);
        EditText minutes=new EditText(this);minutes.setInputType(2);minutes.setHint("Ex.: 30");page.addView(minutes);
        CheckBox session=new CheckBox(this);session.setText("Concluí uma sessão de exercícios");page.addView(session);
        CheckBox limp=new CheckBox(this);limp.setText("A dor me fez mancar");page.addView(limp);
        CheckBox after=new CheckBox(this);after.setText("Piorou depois do repouso ou no dia seguinte");page.addView(after);
        add("Comentários / atividades realizadas",15,true);
        EditText note=new EditText(this);note.setHint("O que fiz hoje? Como meu pé reagiu?");
        note.setMinLines(3);note.setGravity(Gravity.TOP);page.addView(note);
        page.addView(button("Salvar registro",()->{
            try{
                JSONArray arr=new JSONArray(prefs.getString("records","[]"));
                JSONObject item=new JSONObject();
                item.put("date",new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date()));
                item.put("pain",seek.getProgress());
                item.put("minutes",minutes.getText().toString());
                item.put("session",session.isChecked());
                item.put("limp",limp.isChecked());
                item.put("worse",after.isChecked());
                item.put("note",note.getText().toString());
                arr.put(item);
                prefs.edit().putString("records",arr.toString()).putBoolean("limping",limp.isChecked()).apply();
                if (limp.isChecked() || seek.getProgress()>=7 || after.isChecked()) {
                    new android.app.AlertDialog.Builder(this).setTitle("Atenção à dor")
                      .setMessage("Não progrida a carga. Se está mancando, com dor forte ou piora após a sessão, suspenda exercícios provocativos e busque avaliação profissional.")
                      .setPositiveButton("Entendi",(d,w)->show("historico")).show();
                }else show("historico");
            }catch(Exception e){Toast.makeText(this,"Erro ao salvar registro",Toast.LENGTH_LONG).show();}
        }));
    }
    private void history() {
        add("Seu histórico",22,true);
        try{
            JSONArray arr=new JSONArray(prefs.getString("records","[]"));
            add(arr.length()+" registros salvos somente neste aparelho.",14,false);
            if(arr.length()==0)card("Sem registros","Acesse Registrar para começar o acompanhamento.");
            for(int i=arr.length()-1;i>=0;i--){
                JSONObject o=arr.getJSONObject(i);
                String line="Dor: "+o.optInt("pain")+"/10 • Minutos: "+o.optString("minutes","—")
                  +"\nSessão: "+(o.optBoolean("session")?"Sim":"Não")+" • Mancou: "+(o.optBoolean("limp")?"Sim":"Não")
                  +" • Piorou depois: "+(o.optBoolean("worse")?"Sim":"Não")
                  +"\n"+o.optString("note");
                card(o.optString("date"),line);
            }
            page.addView(button("Compartilhar / exportar histórico",()->{
                StringBuilder result=new StringBuilder("Meu Pé • Fisio — Histórico\n");
                try{
                    for(int i=0;i<arr.length();i++){
                        JSONObject o=arr.getJSONObject(i);
                        result.append(o.optString("date")).append(" | Dor ").append(o.optInt("pain"))
                         .append("/10 | Min ").append(o.optString("minutes"))
                         .append(" | Sessão ").append(o.optBoolean("session"))
                         .append(" | Mancou ").append(o.optBoolean("limp"))
                         .append(" | Piorou ").append(o.optBoolean("worse"))
                         .append(" | ").append(o.optString("note")).append("\n");
                    }
                }catch(Exception ignored){}
                Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/plain");
                share.putExtra(Intent.EXTRA_TEXT,result.toString());
                startActivity(Intent.createChooser(share,"Exportar histórico"));
            }));
        }catch(Exception e){add("Não foi possível ler os registros.",14,false);}
    }
    private void settings() {
        add("Preferências",22,true);
        CheckBox clearance=new CheckBox(this);
        clearance.setText("Profissional avaliou o pé e liberou exercícios com carga");
        clearance.setChecked(prefs.getBoolean("cleared",false));page.addView(clearance);
        clearance.setOnCheckedChangeListener((box,checked)->{
            prefs.edit().putBoolean("cleared",checked).apply();
            if(!checked)prefs.edit().putInt("phase",0).apply();
        });
        card("Importante","Marque apenas se houve avaliação profissional e liberação. Este controle não substitui diagnóstico nem monitora a execução.");
        CheckBox reminders=new CheckBox(this);reminders.setText("Ativar dois lembretes diários");
        reminders.setChecked(prefs.getBoolean("reminders",false));page.addView(reminders);
        reminders.setOnCheckedChangeListener((b,checked)->{
            prefs.edit().putBoolean("reminders",checked).apply();
            if(checked){
                if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);
                scheduleAll();
            }else {ReminderReceiver.cancel(this,101);ReminderReceiver.cancel(this,102);}
        });
        add("Manhã: "+prefs.getInt("morning",9)+":00",16,true);
        page.addView(button("Escolher horário da manhã",()->pickTime("morning",9)));
        add("Noite: "+prefs.getInt("evening",19)+":00",16,true);
        page.addView(button("Escolher horário da noite",()->pickTime("evening",19)));
        card("Privacidade","Os dados ficam no aparelho. Exporte o histórico antes de desinstalar. Os lembretes usam notificações Android e podem chegar com pequeno atraso devido à economia de bateria.");
        add("Versão 1.0.0",14,false);
    }
    private void pickTime(String key,int fallback){
        int current=prefs.getInt(key,fallback);
        new TimePickerDialog(this,(view,hour,minute)->{
            prefs.edit().putInt(key,hour).apply();
            scheduleAll();show("ajustes");
        },current,0,true).show();
    }
    private void scheduleAll(){
        if(prefs.getBoolean("reminders",false)){
            ReminderReceiver.schedule(this,prefs.getInt("morning",9),0,101);
            ReminderReceiver.schedule(this,prefs.getInt("evening",19),0,102);
        }
    }
}
