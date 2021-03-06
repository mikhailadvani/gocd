/*
 * Copyright 2018 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

package com.thoughtworks.go.apiv1.currentuser

import com.thoughtworks.go.api.SecurityTestTrait
import com.thoughtworks.go.api.spring.ApiAuthenticationHelper
import com.thoughtworks.go.apiv1.user.representers.UserRepresenter
import com.thoughtworks.go.domain.User
import com.thoughtworks.go.server.service.UserService
import com.thoughtworks.go.server.service.result.LocalizedOperationResult
import com.thoughtworks.go.spark.ControllerTrait
import com.thoughtworks.go.spark.SecurityServiceTrait
import com.thoughtworks.go.spark.mocks.TestRequestContext
import com.thoughtworks.go.util.TriState
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.invocation.InvocationOnMock

import static org.mockito.ArgumentMatchers.any
import static org.mockito.ArgumentMatchers.eq
import static org.mockito.Mockito.doAnswer
import static org.mockito.Mockito.when
import static org.mockito.MockitoAnnotations.initMocks

class CurrentUserControllerDelegateTest implements ControllerTrait<CurrentUserControllerDelegate>, SecurityServiceTrait {

  @Mock
  UserService userService

  @BeforeEach
  void setUp() {
    initMocks(this)
  }

  @Nested
  class Show {
    @Nested
    class Security implements SecurityTestTrait {
      @Test
      void 'should allow nobody with security disabled'() {
        disableSecurity()

        makeHttpCall()
        assertRequestAuthorized()
      }


      @Test
      void 'should not allow anonymous with security enabled'() {
        enableSecurity()
        loginAsAnonymous()

        makeHttpCall()
        assertRequestNotAuthorized()
      }

      @Test
      void 'should allow admin users with security enabled'() {
        enableSecurity()
        loginAsAdmin()

        makeHttpCall()
        assertRequestAuthorized()
      }

      @Test
      void 'should allow normal users with security enabled'() {
        enableSecurity()
        loginAsUser()

        makeHttpCall()
        assertRequestAuthorized()
      }

      @Override
      String getControllerMethodUnderTest() {
        return "show"
      }

      @Override
      void makeHttpCall() {
        getWithApiHeader(controller.controllerBasePath())
      }
    }

    @Nested
    class AsAuthorizedUser {
      User user

      @BeforeEach
      void setUp() {
        user = new User('jdoe', 'Jon Doe', ['jdoe', 'jdoe@example.com'] as String[], 'jdoe@example.com', true)
        enableSecurity()
        loginAsUser()

        when(userService.findUserByName(currentUserLoginName().toString())).thenReturn(user)
      }

      @Test
      void 'should render current user'() {
        getWithApiHeader(controller.controllerBasePath())

        assertThatResponse()
          .isOk()
          .hasEtag('"' + controller.etagFor(UserRepresenter.toJSON(user, new TestRequestContext())) + '"')
          .hasContentType(controller.mimeType)
          .hasJsonBodySerializedWith(user, UserRepresenter.class)
      }

      @Test
      void 'should render 304 if content matches'() {
        def etag = '"' + controller.etagFor(UserRepresenter.toJSON(user, new TestRequestContext())) + '"'
        getWithApiHeader(controller.controllerBasePath(), ['if-none-match': etag])

        assertThatResponse()
          .isNotModified()
          .hasContentType(controller.mimeType)
          .hasNoBody()
      }
    }
  }


  @Nested
  class Update {
    @Nested
    class Security implements SecurityTestTrait {
      @Test
      void 'should allow nobody with security disabled'() {
        disableSecurity()

        makeHttpCall()
        assertRequestAuthorized()
      }

      @Test
      void 'should not allow anonymous with security enabled'() {
        enableSecurity()
        loginAsAnonymous()

        makeHttpCall()
        assertRequestNotAuthorized()
      }

      @Test
      void 'should allow admin users with security enabled'() {
        enableSecurity()
        loginAsAdmin()

        makeHttpCall()
        assertRequestAuthorized()
      }

      @Test
      void 'should allow normal users with security enabled'() {
        enableSecurity()
        loginAsUser()

        makeHttpCall()
        assertRequestAuthorized()
      }

      @Override
      String getControllerMethodUnderTest() {
        return "update"
      }

      @Override
      void makeHttpCall() {
        patchWithApiHeader(controller.controllerBasePath(), [:])
      }
    }

    @Nested
    class AsAuthorizedUser {
      User user = new User('jdoe', 'Jon Doe', ['jdoe', 'jdoe@example.com'] as String[], 'jdoe@example.com', true)

      @BeforeEach
      void setUp() {
        enableSecurity()
        loginAsUser()

        when(userService.findUserByName(currentUserLoginName().toString())).thenReturn(user)
      }

      @Test
      void 'should save supplied user'() {
        def data = [
          login_name     : user.name,
          enabled        : false, // enabled has no effect
          email_me       : false,
          email          : 'foo@example.com',
          checkin_aliases: 'foo, bar'
        ]

        User newUser = new User(user.name, user.displayName, data.checkin_aliases.split(', '), 'foo@example.com', false)

        doAnswer({ InvocationOnMock invocation ->
          return newUser
        }).when(userService).save(eq(user), eq(TriState.from(null)), eq(TriState.from(data.email_me.toString())), eq(data.email), eq(data.checkin_aliases), any() as LocalizedOperationResult)
        patchWithApiHeader(controller.controllerBasePath(), data)

        assertThatResponse()
          .isOk()
          .hasEtag('"' + controller.etagFor(UserRepresenter.toJSON(newUser, new TestRequestContext())) + '"')
          .hasContentType(controller.mimeType)
          .hasJsonBodySerializedWith(newUser, UserRepresenter.class)
      }

      @Test
      void 'should render 304 if content matches'() {
        def etag = '"' + controller.etagFor(UserRepresenter.toJSON(user, new TestRequestContext())) + '"'
        getWithApiHeader(controller.controllerBasePath(), ['if-none-match': etag])

        assertThatResponse()
          .isNotModified()
          .hasContentType(controller.mimeType)
          .hasNoBody()
      }
    }
  }

  @Override
  CurrentUserControllerDelegate createControllerInstance() {
    return new CurrentUserControllerDelegate(new ApiAuthenticationHelper(securityService, goConfigService), userService)
  }
}
