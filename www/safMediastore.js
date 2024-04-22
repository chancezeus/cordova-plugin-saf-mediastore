/*global cordova, module*/

module.exports = (function () {
  function wrap(name, params) {
    return new Promise(function (resolve, reject) {
      cordova.exec(
        resolve,
        reject,
        'SafMediastore',
        name,
        params
      )
    });
  }

  function isBase64(value) {
    try {
      var decoded = atob(value);

      return btoa(decoded) === value;
    } catch (e) {
      return false;
    }
  }

  function prepareParams(params) {
    let data = params.data;
    if (!data) {
      throw new Error('missing data');
    }

    if (typeof data === 'string' && isBase64(data)) {
      return Promise.resolve(data);
    }

    if (!params.mimeType && data instanceof Blob) {
      params.mimeType = data.type;
    }

    if (typeof data === 'string' || data instanceof ArrayBuffer || ArrayBuffer.isView(data)) {
      data = new Blob([data]);
    }

    if (!(data instanceof Blob)) {
      throw new Error('invalid data type, expected (base64) string, Blob, ArrayBuffer or ArrayBufferView')
    }

    return new Promise(function (resolve, reject) {
      const reader = new FileReader();

      reader.onerror = function () {
        reject(reader.error);
      };

      reader.onload = function () {
        resolve(reader.result.substring(reader.result.indexOf(',') + 1));
      };

      reader.readAsDataURL(data);
    }).then(function (data) {
      return {...params, data};
    });
  }

  let exports = {};

  return Object.freeze({
    selectFolder(params) {
      return wrap('selectFolder', [params]);
    },
    selectFile(params) {
      return wrap('selectFile', [params]);
    },
    openFolder(params) {
      return wrap('openFolder', [params]);
    },
    openFile(params) {
      return wrap('openFile', [params]);
    },
    readFile(params) {
      return wrap('readFile', [params]).then(function (result) {
        return new Blob([atob(result.data)], {type: result.type});
      });
    },
    saveFile(params) {
      return prepareParams(params).then(function (prepared) {
        return wrap('saveFile', [prepared]);
      });
    },
    writeFile(params) {
      return prepareParams(params).then(function (prepared) {
        return wrap('writeFile', [prepared]);
      });
    },
    writeMedia(params) {
      return prepareParams(params).then(function (prepared) {
        return wrap('writeMedia', [prepared]);
      });
    },
    overwriteFile(params) {
      return prepareParams(params).then(function (prepared) {
        return wrap('overwriteFile', [prepared]);
      });
    },
    deleteFile(params) {
      return wrap('deleteFile', [params]);
    },
    getInfo(params) {
      return wrap('getInfo', [params]);
    },
    getUri(params) {
      return wrap('getUri', [params]);
    }
  });
})();
