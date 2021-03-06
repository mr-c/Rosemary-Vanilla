# Copyright (C) 2016  Academic Medical Center of the University of Amsterdam (AMC)
#  
# This program is semi-free software: you can redistribute it and/or modify it
# under the terms of the Rosemary license. You may obtain a copy of this
# license at:
# 
# https://github.com/AMCeScience/Rosemary-Vanilla/blob/master/LICENSE.md
# 
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
# See the License for the specific language governing permissions and
# limitations under the License.
#  
# You should have received a copy of the Rosemary license
# along with this program. If not, 
# see https://github.com/AMCeScience/Rosemary-Vanilla/blob/master/LICENSE.md.
#  
#        Project: https://github.com/AMCeScience/Rosemary-Vanilla
#        AMC eScience Website: http://www.ebioscience.amc.nl/

module = angular.module 'Rosemary'
module.controller 'RegisterController', ($scope, $rootScope, $state, Auth) ->

  new class RegisterCtrl extends DefaultController

    ###########################
    # Instance variables      #
    ###########################

    credentials:
      email: ""
      name: ""
      password: ""
      password_check: ""

    errors:
      email: []
      name: []
      password: []
      password_check: []

    descriptions:
      'error.email': 'Please provide a valid email-adress'
      'error.minLength': 'The provided {{ name }} isn\'t long enough'
      'equal': 'The password and check don\'t match'
      'duplicate': 'This user already exists'

    ###########################
    # constructor & init      #
    ###########################

    constructor: ->
      super $scope, $rootScope

      @scope.$watch 'ctrl.credentials', @checkForm, true

    ###########################
    # Methods                 #
    ###########################

    checkForm: =>
      @checkEmail()
      @checkPassword()

    checkEmail: =>
      @errors.email = 
        if /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,4}$/.test(@credentials.email) || @credentials.email.length < 6 then []
        else [ @descriptions['error.email'] ]

    checkPassword: =>
      @errors.password_check =
        if @credentials.password is @credentials.password_check then []
        else [ @descriptions.equal ]

    register: =>
      @errors.email = []
      @errors.name = []
      @errors.password = []

      Auth.register @credentials
      .then (-> $rootScope.nav.data()), (error) =>
        console.info error
        _.forEach error.data, (v) =>
          console.log v
          name = v.path.substring 1
          @errors[name] = _.map v.errors, (type) =>
            @descriptions[type]