package com.liferay.portlet.atlassian.jira.action;

import com.liferay.portlet.atlassian.jira.JiraProxy;
import com.liferay.portlet.atlassian.jira.model.CalendarPropertyEditor;
import com.liferay.portlet.atlassian.jira.model.ComponentsPropertyEditor;
import com.liferay.portlet.atlassian.jira.model.Issue;
import com.liferay.portlet.atlassian.jira.model.IssueType;
import com.liferay.portlet.atlassian.jira.model.IssueTypePropertyEditor;
import com.liferay.portlet.atlassian.jira.model.Priority;
import com.liferay.portlet.atlassian.jira.model.PriorityPropertyEditor;
import com.liferay.portlet.atlassian.jira.model.Project;
import com.liferay.portlet.atlassian.jira.model.ProjectPropertyEditor;
import com.liferay.portlet.atlassian.jira.model.Version;
import com.liferay.portlet.atlassian.jira.model.VersionsPropertyEditor;
import com.liferay.portlet.atlassian.jira.util.StringUtil;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.portlet.ModelAndView;
import org.springframework.web.portlet.bind.PortletRequestDataBinder;
import org.springframework.web.portlet.mvc.AbstractWizardFormController;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletPreferences;
import javax.portlet.PortletRequest;
import javax.portlet.PortletSession;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CreateTicketPortletAction
    extends AbstractWizardFormController {


    protected Map referenceData(final PortletRequest request,
                                final Object command,
                                final Errors errors, final int page)
        throws Exception {
        final PortletSession session = request.getPortletSession();
        JiraProxy proxy =
            (JiraProxy) session.getAttribute(JiraPortletConstants.PROXY_KEY);
        String securityToken =
            (String) session.getAttribute(
                JiraPortletConstants.SECURITY_TOKEN_KEY);
        if (proxy == null || StringUtil.isEmpty(securityToken)) {
            throw new IllegalStateException("Portlet not connected to Jira");
        }
        final Issue issue = (Issue) command;
        final Map<String, Object> values = new HashMap<String, Object>();
        switch (page) {
            case 0: //should retrieve project information and etc
                values.put(JiraPortletConstants.PROJECTS_KEY,
                           proxy.getProjects(securityToken));
                values.put(JiraPortletConstants.ISSUE_TYPES_KEY,
                           proxy.getIssueTypes(securityToken));
                return values;
            case 1: //should retrieve project information and etc
                values.put(JiraPortletConstants.PROJECT_KEY,
                           issue.getProject());
                values.put(JiraPortletConstants.ISSUE_TYPE_KEY,
                           issue.getIssueType());
                values.put(JiraPortletConstants.PRIORITIES_KEY,
                           proxy.getPriorities(securityToken));
                final String reporterName =
                    request.getPreferences().
                        getValue(JiraPortletConstants.USER_NAME_PREFERENCE,
                                 StringUtil.EMPTY_STRING);
                values.put(JiraPortletConstants.REPORTER_KEY,
                           reporterName);
                issue.setReporterName(reporterName);
                values.put(JiraPortletConstants.COMPONENTS_KEY,
                           proxy.getComponents(securityToken,
                                               issue.getProject().getKey()));
                final Collection<Version> versions =
                    proxy.getVersions(securityToken,
                                      issue.getProject().getKey());
                values.put(JiraPortletConstants.VERSIONS_KEY, versions);
                return values;
            default:
                return null;
        }

    }

    protected void validatePage(Object command, Errors errors, int page,
                                boolean finish) {
        if (finish) {
            getValidator().validate(command, errors);
        }
    }

    protected void processFinish(final ActionRequest request,
                                 final ActionResponse response,
                                 final Object command,
                                 final BindException errors)
        throws Exception {
        final PortletSession session = request.getPortletSession();
        JiraProxy proxy =
            (JiraProxy) session.getAttribute(JiraPortletConstants.PROXY_KEY);
        if (proxy == null) {
            return;
        }
        String securityToken =
            (String) session.getAttribute(
                JiraPortletConstants.SECURITY_TOKEN_KEY);
        if (StringUtil.isEmpty(securityToken)) {
            return;
        }

        final Issue issue = (Issue) command;
        final String issueKey = proxy.createIssue(securityToken, issue);
        response.setRenderParameter(JiraPortletConstants.TICKET_ID_KEY,
                                    issueKey);
    }

    protected ModelAndView renderFinish(final RenderRequest request,
                                        final RenderResponse response,
                                        final Object command,
                                        final BindException errors)
        throws Exception {
        final String ticketId =
            request.getParameter(JiraPortletConstants.TICKET_ID_KEY);

        final ModelAndView view = new ModelAndView("createTicketFinish");
        view.addObject(JiraPortletConstants.TICKET_ID_KEY, ticketId);

        final PortletPreferences prefs = request.getPreferences();
        final String url =
            prefs.getValue(JiraPortletConstants.USER_NAME_PREFERENCE,
                           StringUtil.EMPTY_STRING);

        view.addObject(JiraPortletConstants.URL_PREFERENCE, url);
        return view;
    }

    /**
     * Registers a PropertyEditor with the data binder for handling Dates using
     * the format as currently specified in the PortletPreferences.
     */
    protected void initBinder(final PortletRequest request,
                              final PortletRequestDataBinder binder)
        throws Exception {

        binder.registerCustomEditor(Collection.class, "components",
                                    new ComponentsPropertyEditor(
                                        Collection.class,
                                        true));
        binder.registerCustomEditor(Collection.class, "versions",
                                    new VersionsPropertyEditor(
                                        Collection.class,
                                        true));
        binder.registerCustomEditor(Collection.class, "fixedVersions",
                                    new VersionsPropertyEditor(
                                        Collection.class,
                                        true));
        binder.registerCustomEditor(IssueType.class,
                                    new IssueTypePropertyEditor());
        binder.registerCustomEditor(Project.class,
                                    new ProjectPropertyEditor());
        binder.registerCustomEditor(Priority.class,
                                    new PriorityPropertyEditor());
        binder.registerCustomEditor(Calendar.class,
                                    new CalendarPropertyEditor(_DATE_FORMAT));

        binder.setAllowedFields(new String[]{"project", "issueType",
            "priority", "summary", "reporterName",
            "dueDate", "components", "versions",
            "fixedVersions", "assigneeName", "environment",
            "description"});
    }

    private static final String _DATE_FORMAT = "MM/dd/yyyy";
}