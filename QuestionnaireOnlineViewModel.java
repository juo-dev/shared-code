package com.mongeral.vendaDigital.viewModel.questionnaire;

import android.net.http.SslError;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.databinding.ObservableBoolean;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mongeral.vendaDigital.R;
import com.mongeral.vendaDigital.jsonModel.questionnaire.JSONQuestionnaire;
import com.mongeral.vendaDigital.manager.SystemTokenManager;
import com.mongeral.vendaDigital.model.ProposalEntity;
import com.mongeral.vendaDigital.model.observable.ObservableString;
import com.mongeral.vendaDigital.model.proposalmodel.Questionnaire;
import com.mongeral.vendaDigital.model.questionnaire.QuestionnaireEntity;
import com.mongeral.vendaDigital.model.questionnaire.QuestionnaireItemEntity;
import com.mongeral.vendaDigital.repository.QuestionnaireRepository;
import com.mongeral.vendaDigital.viewModel.FormProposalViewModel;
import com.mongeral.vendaDigital.viewModel.WidgetViewModel;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.realm.RealmList;

public class QuestionnaireOnlineViewModel extends FormProposalViewModel implements WidgetViewModel {

    public final ObservableString widgetUrl = new ObservableString();
    public final ObservableBoolean showProgressBar = new ObservableBoolean(false);
    public final ObservableBoolean isQuestionnairesAnswered = new ObservableBoolean();
    protected final RealmList<QuestionnaireItemEntity> questionnaireItems = new RealmList<>();
    protected final List<Integer> questionnaireIds = new ArrayList<>();
    protected int questionnaireIdIndex = 0;
    @Inject
    protected QuestionnaireRepository questionnaireRepository;

    public QuestionnaireOnlineViewModel(ProposalEntity proposal) {
        super(proposal);
        getComponent().inject(this);
        isQuestionnairesAnswered.set(proposal.getQuestionnaire().isOnlineQuestionnairesAnswered());
    }

    public void start() {
        if (isQuestionnairesAnswered.get()) {
            QuestionnaireEntity questionnaire = proposal.getQuestionnaire();
            questionnaireItems.addAll(questionnaire.getQuestionnaireItems());
            return;
        }

        showProgressBar.set(true);

        List<Questionnaire> enabledQuestionnaires = questionnaireRepository.getEnabledQuestionnaires(proposal);
        enabledQuestionnaires.forEach(q -> questionnaireIds.add(q.getId()));

        if (questionnaireIds.isEmpty()) {
            showProgressBar.set(false);
            isQuestionnairesAnswered.set(true);
        } else
            setWidgetUrl();
    }

    protected void setWidgetUrl() {
        String widgetUrl = context.getString(R.string.url_widget_questionnaire,
                questionnaireIds.get(questionnaireIdIndex),
                proposal.getProposalNumber(),
                context.getString(R.string.questionnaire_primary_color),
                context.getString(R.string.questionnaire_secondary_color));
        this.widgetUrl.set(widgetUrl);
    }

    @Override
    public WebViewClient getWebViewClient() {
        return new WebViewClient() {

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                super.onReceivedSslError(view, handler, error);
                showProgressBar.set(false);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                view.loadUrl("javascript:window.postMessage({event: 'notify', property: 'Token', value: '" + SystemTokenManager.getToken().getAccessToken() + "' })");
                String message = getMessage();
                if (StringUtils.isNotEmpty(message))
                    view.loadUrl("javascript:window.postMessage({event: 'notify', property: 'QuestionaireHiddenValues', value: '" + message + "' })");
                view.loadUrl("javascript:window.addEventListener('message', response => HtmlViewer.getResponse(JSON.stringify(response.data)))");
                showProgressBar.set(false);
            }
        };
    }

    protected String getMessage() {
        return null;
    }

    @Override
    public WidgetJavaScriptResponse getJavascriptInterface() {
        return new WidgetJavaScriptResponse(this);
    }

    @Override
    public void onResponse(String response) {
        response = response.replaceAll("\\\\r", "")
                .replaceAll("\\\\n", "")
                .replaceAll("\\\\", "")
                .replaceAll("\"\\{", "{")
                .replaceAll("\\}\"", "}");
        JsonObject jsonResponse = new Gson().fromJson(response, JsonObject.class);
        if (!jsonResponse.has("tipo"))
            return;
        String tipo = jsonResponse.get("tipo").getAsString();
        if (!"Resposta".equals(tipo))
            return;
        JsonObject resposta = jsonResponse.get("Resposta").getAsJsonObject();
        JSONQuestionnaire jsonQuestionnaire = new Gson().fromJson(resposta, JSONQuestionnaire.class);
        QuestionnaireItemEntity questionnaireItem = new QuestionnaireItemEntity(jsonQuestionnaire);
        if (wasAlreadyAdded(questionnaireItem.getCode()))
            return;
        questionnaireItem.setSent(true);
        questionnaireItems.add(questionnaireItem);
        if (++questionnaireIdIndex < questionnaireIds.size())
            setWidgetUrl();
        else
            isQuestionnairesAnswered.set(true);
    }

    protected boolean wasAlreadyAdded(int code) {
        for (QuestionnaireItemEntity questionnaireItem : questionnaireItems)
            if (questionnaireItem.getCode() == code)
                return true;
        return false;
    }

    @Override
    public String validateAndGetErrors() {
        StringBuilder errors = new StringBuilder();
        if (!isQuestionnairesAnswered.get())
            errors.append(context.getString(R.string.questionnaire_answer_all));
        return errors.toString();
    }

    @Override
    public void save() {
        QuestionnaireEntity questionnaire = proposal.getQuestionnaire();
        questionnaire.setQuestionnaireItems(questionnaireItems);
        questionnaire.setOnlineQuestionnairesAnswered(true);
    }
}
