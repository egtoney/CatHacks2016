#!/usr/bin/python

import sys
sys.path.append('/usr/bin/python3.4')
import sublime
import sublime_plugin
import threading
import socket, json
import ssl 

class ModificationListener(sublime_plugin.EventListener):
	prev_pos = {}

	def on_modified(self, view):
		view_id = view.id()
		sel = view.sel()[0].begin()
		if view.file_name() != None:
			file_name = view.file_name().split("/")[-1]
			ML = ModificationListener
			if sel > ML.prev_pos[view_id]:
				region = sublime.Region(ML.prev_pos[view_id], sel)
			else:
				region = sublime.Region(sel, ML.prev_pos[view_id])

	def on_activated(self, view):
		view_id = view.id()
		sel = view.sel()[0]
		ML = ModificationListener
		ML.prev_pos[view_id] = sel.begin()

	def on_selection_modified(self, view):
		view_id = view.id()
		sel = view.sel()[0]
		ML = ModificationListener
		if sel.begin() == sel.end():
			ML.prev_pos[view_id] = sel.begin()

class connectServerCommand(sublime_plugin.TextCommand):
	address = ''
	pwd = ''

	def run(self, view):
		self.view.window().show_input_panel("Address Input:",
			"0.0.0.0", self.on_done1, None, None)
	def on_done1(self, user_input):
		self.address = user_input
		self.view.window().show_input_panel("Address Input:",
			"", self.on_done2, None, None)
	def on_done2(self, user_input):
		self.pwd = user_input
		if handler.connect(self.address, self.pwd) != True:
			sublime.error_message("Unable to connect to server")
		else:
			sublime.status_message("Connected")
			handler.startThread()

class killConnectionCommand(sublime_plugin.TextCommand):
	def run(self, view):
		handler.closeConnections()

class Handler(object):
	def __init__(self):
		self.client = Client()

	def connect(self, address, pwd):
		return self.client.connect(address, pwd)

	def startThread(self):
		self.client.startReadThread()

	def closeConnections(self):
		self.client.closeConnections()

class Client(object):
	session_id = ''
	port = 0
	pwd = ''

	MSG_ID_LOGIN = 0
	MSG_ID_NEW_FILE = 1
	MSG_ID_INSERT = 2
	MSG_ID_PULL_CHANGES = 3
	MSG_ID_AVAILABLE_FILES = 4
	MSG_ID_GET_FILES = 5
	MSG_ID_DELETE = 6

	def __init__(self):
		self.s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

		# Require a certificate from the server
		self.ssl_sock = ssl.wrap_socket(self.s, 
			cert_reqs=ssl.CERT_NONE)
		self.ssl_sock.settimeout(1.0)

	def connect(self, t_id, t_pwd):
		self.session_id = t_id
		self.port = 22753
		self.pwd  = t_pwd
		try:
			self.ssl_sock.connect((self.session_id, self.port))
		except:
			return False
		else:
			return True

	def closeConnections(self):
		self.ssl_sock.close()

	def startReadThread(self):
		self.read_thread = readThread(1, "Thread-1", this.ssl_sock)

	def login(self):
		msg = {
			'ID': MSG_ID_LOGIN,
			'session_id': session_id,
			'pwd': pwd,
		}
		
		if self.ssl_sock.sendall(json.dumps(msg)) == None:
			return False
		else:
			data = self.ssl_sock.recv(1024)
			parsed_json = json.loads(data)
			if parsed_json['yay'] == 1:
				return True
			else:
				return False

	def killConnection(self):
		self.ssl_sock.close()

	def pullChanges(self, username, target_user):
		msg = {
			'ID': MSG_ID_PULL_CHANGES,
			'username': username,
			'target_user': target_user,
		}

		if self.ssl_sock.sendall(json.dumps(msg)) == None:
			return False
		else:
			return True

	def sendCharEdit(self, char, pos, username, filename):
		msg = {
			'ID': MSG_ID_CHAR_EDIT,
			'char': char,
			'pos': pos,
			'username': username,
			'filename': filename,
		}

		if self.ssl_sock.sendall(json.dumps(msg)) == None:
			return False
		else:
			return True

	def addNewFile(self, filename, username):
		msg = {
			'ID': MSG_ID_NEW_FILE,
			'filename': filename,
			'username': username,
		}

		if self.ssl_sock.sendall(json.dumps(msg)) == None:
			return False
		else:
			return True

class readThread(threading.Thread):
	def __init__(self, threadID, name, t_sock):
		threading.Thread.__init__(self)
		self.threadID = threadID
		self.name = name
		self.ssl_sock = t_sock
	def run(self):
		try:
			while True:
				data = self.ssl_sock.recv(1024)
				if data != None:
					print(data)
		except:
			print("Socket Error!")
		else:
			handler.closeConnections()

handler = Handler()