import sublime, sublime_plugin, threading, socket, json, ssl, re, hashlib, sys, time, select

class ModificationListener(sublime_plugin.EventListener):
	prev_pos = {}

	def on_modified(self, view):
		if handler.client.connected:
			view_id = view.id()
			sel = view.sel()[0].begin()
			if view.file_name() != None:
				ML = ModificationListener

				current_file = view.file_name()
				if current_file[0] != '/':
					sub_string = current_file[4:]
					self.parsed_path = sub_string.split('\\')
				else:
					self.parsed_path = current_file.split('/')

				if sel > ML.prev_pos[view_id]:
					region = sublime.Region(ML.prev_pos[view_id], sel)
					handler.sendInsertion(view.substr(region),
						region, self.parsed_path[-1])
				else:
					region = sublime.Region(sel, ML.prev_pos[view_id])
					handler.sendDeletion(region, self.parsed_path[-1])

	def on_activated(self, view):
		if handler.client.connected:
			view_id = view.id()
			sel = view.sel()[0]
			ML = ModificationListener
			ML.prev_pos[view_id] = sel.begin()

	def on_selection_modified(self, view):
		if handler.client.connected:
			view_id = view.id()
			sel = view.sel()[0]
			ML = ModificationListener
			if sel.begin() == sel.end():
				ML.prev_pos[view_id] = sel.begin()

	def on_post_save(self, view):
		if handler.client.connected:
			print("In post save")
			current_file = view.file_name()
			if current_file[0] != '/':
				sub_string = current_file[4:]
				self.parsed_path = sub_string.split('\\')
			else:
				self.parsed_path = current_file.split('/')

			if current_file not in fileNameList:
				fileNameList.append(current_file)
				handler.addNewFile(current_file[len(absolutePath):], view.substr(sublime.Region(0, view.size())))

class killConnectionCommand(sublime_plugin.TextCommand):
	def run(self, view):
		handler.closeConnections()

class addProjectCommand(sublime_plugin.TextCommand):
	def run(self, view):
		current_project = self.view.window().project_file_name()
		if current_project[0] != '/':
			sub_string = current_project[4:]
			self.parsed_path = sub_string.split('\\')
		else:
			self.parsed_path = sub_string.split('/')

		Handler.addProject(self.parsed_path)

class syncFolderWithServerCommand(sublime_plugin.WindowCommand):
	def run(self, paths=[]):
		absolutePath = paths[0]
		self.window.show_input_panel("Username Input:",
			"", self.on_done1, None, None)

	def on_done1(self, user_input):
		self.username = user_input
		self.window.show_input_panel("Address Input:",
			"0000", self.on_done2, None, None)

	def on_done2(self, user_input):
		self.server_id = user_input
		if re.match("\w{4}", self.server_id) != None:
			self.window.show_input_panel("Password Input:",
				"", self.on_done3, None, None)
		else:
			sublime.error_message("Invalid Server ID")
	def on_done3(self, user_input):
		self.pwd = hashlib.sha224(user_input.encode('utf-8')).hexdigest()
		if handler.connect() != True:
			sublime.error_message("Unable to connect to server")
		else:
			if handler.sendLogin(self.server_id, self.pwd, self.username) == True:
				sublime.status_message("Connected")
				handler.startThread()
			else:
				sublime.error_message("Wrong Login Information")

class Handler(object):
	current_project = ""

	def __init__(self):
		self.client = Client()

	def connect(self):
		return self.client.connect()

	def startThread(self):
		self.client.startReadThread()

	def closeConnections(self):
		self.client.closeConnections()

	def addNewFile(self, file_name, t_file):
		self.client.addNewFile(file_name, t_file)

	def sendInsertion(self, insertion, position, filename):
		self.client.sendInsertion(insertion, position, filename)

	def sendDeletion(self, position, filename):
		self.client.sendDeletion(position, filename)

	def sendLogin(self, t_server_id, t_pwd, t_username):
		return self.client.login(t_server_id, t_pwd, t_username)

	def localInsert(self, json_msg):
		parsed_json = json.loads(json_msg)
		
		if parsed_json['ID'] != "8":
			insertion = parsed_json['insertion']
			position = parsed_json['position']
			username = parsed_json['username']
			filename = parsed_json['filename']

class Client(object):
	proxy_ip = "10.20.216.10"
	server_id = ''
	port = 0
	pwd = ''
	username = ''
	connected = False

	MSG_ID_LOGIN = 0 # Finished
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
		self.ssl_sock.settimeout(1.5)

	def connect(self):
		self.port = 22754
		try:
			self.ssl_sock.connect((self.proxy_ip, self.port))
		except:
			return False

		self.connected = True
		return True

	def closeConnections(self):
		self.ssl_sock.close()

	def startReadThread(self):
		self.read_thread = readThread(1, "Thread-1", self.ssl_sock)
		self.read_thread.start()

	def login(self, t_session_id, t_pwd, t_username):
		self.session_id = t_session_id
		self.pwd = t_pwd
		self.username = t_username
		# msg = {
		# 	'ID': self.MSG_ID_LOGIN,
		# 	'server_id': self.session_id,
		# 	'pwd': self.pwd,aa
		# 	'username': self.usernamea
		# }

		msg = " { \"ID\":\"" + str(self.MSG_ID_LOGIN) + "\"," + "\"server_id\":\"" + self.session_id + "\"," + "\"pwd\":\"" + self.pwd + "\"," + "\"username\":\"" + self.username + "\"}"
		
		# self.ssl_sock.sendall(json.dumps(msg).encode('ascii'))
		self.ssl_sock.sendall(msg.encode('ascii'))
		# self.ssl_sock.flush()
		chunks = ""
		while True:
			chunk = str(self.ssl_sock.recv(1))
			if chunk.endswith("'"): chunk = chunk[:-1]
			if chunk.startswith("b'"): chunk = chunk[2:]
			if chunk != "\\n":
				chunks += chunk 
			else:
				break

		parsed_json = json.loads(chunks)

		if parsed_json['status'] == "1":
			time_diff_with_server = int(parsed_json['server_time']) - current_milli_time()
			print(str(time_diff_with_server))

			return True
		else:
			return False

	def killConnection(self):
		self.ssl_sock.close()

	def pullChanges(self, username, target_user):
		# msg = {
		# 	'ID': MSG_ID_PULL_CHANGES,
		# 	'username': username,
		# 	'target_user': target_user
		# }

		msg = " { \"ID\":\"" + str(self.MSG_ID_PULL_CHANGES) + "\"," + "\"username\":\"" + username + "\"target_user\":\"" + target_user + "\"}"

		# if self.ssl_sock.sendall(json.dumps(msg)) == None:
		if self.ssl_sock.sendall(msg.encode('ascii')) == None:
			return False
		else:
			return True

	def sendInsertion(self, t_insertion, t_position, t_filename):
		# msg = {
		# 	'ID': self.MSG_ID_INSERT,
		# 	'insertion': t_insertion,
		# 	'position': t_position,
		# 	'username': self.username,
		# 	'filename': t_filename
		# }
		msg = " { \"ID\":\"" + str(self.MSG_ID_INSERT)  + "\"," + "\"timestamp\":\"" + str(current_milli_time())+ "\"," + "\"text\":\"" + str(t_insertion) + "\"," + "\"pos\":\"" + str(t_position.begin())+ "\"," + "\"username\":\"" + self.username + "\"," + "\"filename\":\"" + t_filename + "\"}"
		
		# self.ssl_sock.write((json.dumps(msg)).encode('ascii'))
		self.ssl_sock.sendall(msg.encode('ascii'))
		
		try:
			self.ssl_sock.write()
		except:
			pass
	
	def sendDeletion(self, t_position, t_filename):
		# msg = {
		# 	'ID': self.MSG_ID_DELETE,
		# 	'position': t_position,
		# 	'username': self.username,
		# 	'filename': t_filename
		# }

		msg = " { \"ID\":\"" + str(self.MSG_ID_DELETE) + "\"," + "\"timestamp\":\"" + str(current_milli_time())+ "\"," + "\"pos\":\"" + str(t_position.begin()) + "\"," + "\"length\":\"" + str(t_position.end() - t_position.begin())+ "\"," + "\"username\":\"" + self.username + "\"," + "\"filename\":\"" + t_filename + "\"}"

		# self.ssl_sock.sendall((json.dumps(msg)).encode('ascii'))
		self.ssl_sock.sendall(msg.encode('ascii'))
		
		try:
			self.ssl_sock.write()
		except:
			pass

	def addNewFile(self, filename, t_file):
		# msg = {
		# 	'ID': MSG_ID_NEW_FILE,
		# 	'filename': filename,
		# 	'username': usernamess
		# }

		msg = " { \"ID\":\"" + str(self.MSG_ID_NEW_FILE) + "\"," + "\"username\":\"" + self.username + "\"," + "\"filename\":\"" + filename + "\"," + "\"file\":\"" + re.escape(t_file) + "\"}"
		# if self.ssl_sock.sendall(json.dumps(msg)) == None:
		if self.ssl_sock.sendall(msg.encode('ascii')) == None:
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
		timeout_in_seconds = 5
		try:
			while True:
				chunks = ''

				while True:
					ready = select.select([self.ssl_sock], [], [], timeout_in_seconds)
					if ready[0]:
						chunk = str(self.ssl_sock.recv(1))
						if chunk.endswith("'"): chunk = chunk[:-1]
						if chunk.startswith("b'"): chunk = chunk[2:]
						if chunk != "\\n":
							chunks += chunk 
						else:
							break

				handler.localInsert(chunks)
		except:
			print("Unexpected error", sys.exc_info()[0])
			handler.closeConnections()
			raise
		else:
			handler.closeConnections()

handler = Handler()
absolutePath = ""
time_diff_with_server = 0
current_milli_time = lambda: int(round(time.time()) * 1000) + time_diff_with_server
fileNameList = []