import os
from os import mkdir
from typing import IO

import requests


def load_password(path='nc_api_bot_password.cred'):
    if not os.path.exists(path):
        print('No password defined. Please create ' + path)
        exit(1)
    with open(path) as pwf:
        return pwf.read()


class ApiWrapper:
    password = load_password()

    def fetchUsers(self):
        return requests.get('https://warmulla.kaleidox.de/ocs/v1.php/cloud/users',
                            headers={'Accepts': 'application/json', 'OCS-APIRequest': 'true'},
                            auth=('bot', self.password)).json().ocs.data.users

    def mkdirs(self, path):
        for blob in path.split('/'):
            mkdir(blob)

    def mkdir(self, path):
        requests.request("MKCOL", f'https://warmulla.kaleidox.de/remote.php/dav/files/bot/{path}',
                         headers={'OCS-APIRequest': 'true'},
                         auth=('bot', self.password))

    def upload(self, path, data):
        for blob in path.split('/')[:-1]: mkdir(blob)
        requests.put(f'https://warmulla.kaleidox.de/remote.php/dav/files/bot/{path}', data,
                      headers={'OCS-APIRequest': 'true'},
                      auth=('bot', self.password))

    def share(self, path, group, permissions: int = 31, download: bool = True):
        qp = {
            'path': path,
            'shareType': 1,
            'shareWith': group,
            'permissions': permissions
        }
        if download: qp['attributes'] \
            = '%5B%7B%22scope%22%3A%22permissions%22%2C%22key%22%3A%22download%22%2C%22value%22%3Atrue%7D%5D'
        requests.post('https://warmulla.kaleidox.de/ocs/v2.php/apps/files_sharing/api/v1/shares',
                      params=qp,
                      headers={'OCS-APIRequest': 'true'},
                      auth=('bot', self.password))

    def fetchTableData(self, tableId):
        return requests.get(f'https://warmulla.kaleidox.de/index.php/apps/tables/api/1/tables/{tableId}/rows',
                            headers={'Accepts': 'application/json'},
                            auth=('bot', self.password))
