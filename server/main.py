import os
from flask import Flask, request, redirect, url_for
from werkzeug import secure_filename

UPLOAD_FOLDER = './uploads'
ALLOWED_EXTENSIONS = set(['bin', 'csv'])

app = Flask(__name__)
app.debug = True
app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER

def allowed_file(filename):
    return '.' in filename and \
           filename.rsplit('.', 1)[1] in ALLOWED_EXTENSIONS

    
@app.route('/upload', methods=['POST'])
def handle_upload():
    filename = request.args.get('filename')
    filename = secure_filename(filename)
    server_filename = os.path.join(app.config['UPLOAD_FOLDER'], filename)
    assert filename is not None
    with open(server_filename, 'w') as f:
        f.write(request.stream.read())
    return '', 200


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
