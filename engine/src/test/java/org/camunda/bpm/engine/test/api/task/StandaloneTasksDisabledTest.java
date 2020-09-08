/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.test.api.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import java.util.List;

import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.CaseService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.ProcessDefinitionPermissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.authorization.TaskPermissions;
import org.camunda.bpm.engine.exception.NotAllowedException;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.task.Task;
import org.camunda.bpm.engine.test.Deployment;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;

public class StandaloneTasksDisabledTest {

  @ClassRule
  public static ProcessEngineBootstrapRule bootstrapRule = new ProcessEngineBootstrapRule(p ->
     p.setStandaloneTasksEnabled(false));

  public ProvidedProcessEngineRule engineRule = new ProvidedProcessEngineRule(bootstrapRule);
  public ProcessEngineTestRule engineTestRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(engineTestRule);

  @Rule
  public ExpectedException exception = ExpectedException.none();

  private RuntimeService runtimeService;
  private TaskService taskService;
  private IdentityService identityService;
  private CaseService caseService;


  @Before
  public void setUp() throws Exception {
    runtimeService = engineRule.getRuntimeService();
    taskService = engineRule.getTaskService();
    identityService = engineRule.getIdentityService();
    caseService = engineRule.getCaseService();
  }

  @After
  public void tearDown() {
    identityService.clearAuthentication();
    engineRule.getProcessEngineConfiguration().setAuthorizationEnabled(false);
    engineTestRule.deleteAllAuthorizations();
    engineTestRule.deleteAllStandaloneTasks();
  }

  @Test
  public void missingTests() {
    fail("standaloneTasksEnabled default value is true");
    fail("adapt testTaskQueryAuthorization");
  }

  @Test
  public void shouldNotCreateStandaloneTask() {
    // given
    Task task = taskService.newTask();

    // then
    exception.expect(NotAllowedException.class);
    exception.expectMessage("Cannot save standalone task. They are disabled in the process engine configuration.");

    // when
    taskService.saveTask(task);
  }

  @Test
  @Deployment(resources = "org/camunda/bpm/engine/test/api/oneTaskProcess.bpmn20.xml")
  public void shouldAllowToUpdateProcessInstanceTask() {

    // given
    runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task task = taskService.createTaskQuery().singleResult();

    task.setAssignee("newAssignee");

    // when
    taskService.saveTask(task);

    // then
    Task updatedTask = taskService.createTaskQuery().singleResult();
    assertThat(updatedTask.getAssignee()).isEqualTo("newAssignee");
  }

  @Test
  @Deployment(resources = "org/camunda/bpm/engine/test/api/cmmn/oneTaskCase.cmmn")
  public void shouldAllowToUpdateCaseInstanceTask() {

    // given
    caseService.createCaseInstanceByKey("oneTaskCase").getId();
    Task task = taskService.createTaskQuery().singleResult();

    task.setAssignee("newAssignee");

    // when
    taskService.saveTask(task);

    // then
    Task updatedTask = taskService.createTaskQuery().singleResult();
    assertThat(updatedTask.getAssignee()).isEqualTo("newAssignee");
  }

  @Test
  public void testTaskQueryAuthorization() {
    // given
    engineTestRule.deploy("org/camunda/bpm/engine/test/api/oneTaskProcess.bpmn20.xml");
    engineTestRule.deploy("org/camunda/bpm/engine/test/api/twoTasksProcess.bpmn20.xml");

    // a process instance task with read authorization
    ProcessInstance instance1 = runtimeService.startProcessInstanceByKey("oneTaskProcess");
    Task processInstanceTask = taskService.createTaskQuery().processInstanceId(instance1.getId()).singleResult();

    Authorization processInstanceAuthorization = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    processInstanceAuthorization.setResource(Resources.PROCESS_DEFINITION);
    processInstanceAuthorization.setResourceId("oneTaskProcess");
    processInstanceAuthorization.addPermission(ProcessDefinitionPermissions.READ_TASK);
    processInstanceAuthorization.setUserId("user");
    authorizationService.saveAuthorization(processInstanceAuthorization);

    // a standalone task with read authorization
    Task standaloneTask = taskService.newTask();
    taskService.saveTask(standaloneTask);

    Authorization standaloneTaskAuthorization = authorizationService.createNewAuthorization(Authorization.AUTH_TYPE_GRANT);
    standaloneTaskAuthorization.setResource(Resources.TASK);
    standaloneTaskAuthorization.setResourceId(standaloneTask.getId());
    standaloneTaskAuthorization.addPermission(TaskPermissions.READ);
    standaloneTaskAuthorization.setUserId("user");
    authorizationService.saveAuthorization(standaloneTaskAuthorization);

    // a third task for which we have no authorization
    runtimeService.startProcessInstanceByKey("twoTasksProcess");

    identityService.setAuthenticatedUserId("user");
    engineRule.getProcessEngineConfiguration().setAuthorizationEnabled(true);

    // when
    List<Task> tasks = taskService.createTaskQuery().list();

    // then
    assertThat(tasks).extracting("id").containsExactlyInAnyOrder(standaloneTask.getId(), processInstanceTask.getId());
  }
}
